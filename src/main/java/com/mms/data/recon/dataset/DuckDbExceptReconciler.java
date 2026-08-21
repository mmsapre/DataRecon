package com.mms.data.recon.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streams source/target rows into DuckDB and compares with {@code EXCEPT ALL}
 * for detail modes ({@link ReconMode#MISMATCH_DETAILS}, {@link ReconMode#FIELD_DETAILS}).
 * {@link ReconMode#COUNTS} uses hash compare instead (see {@code DatasetRecService}).
 */
@Component
public class DuckDbExceptReconciler {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int APPEND_FLUSH_EVERY = 50_000;

    private final Path snapshotRoot;

    public DuckDbExceptReconciler() {
        this(Path.of("data", "duckdb"));
    }

    public DuckDbExceptReconciler(Path snapshotRoot) {
        this.snapshotRoot = snapshotRoot;
    }

    /** Test / in-memory helper — loads lists via the streaming path. */
    public Result compare(
            DatasetConfiguration dataset,
            List<DataLoadDefinition.RawRow> sourceRows,
            List<DataLoadDefinition.RawRow> targetRows,
            ReconSettings settings,
            RunScope scope) {
        return compare(
                dataset,
                Flux.fromIterable(sourceRows),
                Flux.fromIterable(targetRows),
                settings,
                scope
        );
    }

    /**
     * Streams rows from each Flux into DuckDB (no full materialization), then runs EXCEPT ALL.
     * Call from a bounded elastic scheduler — this method blocks while consuming the fluxes.
     */
    public Result compare(
            DatasetConfiguration dataset,
            Flux<DataLoadDefinition.RawRow> sourceRows,
            Flux<DataLoadDefinition.RawRow> targetRows,
            ReconSettings settings,
            RunScope scope) {

        settings.normalize();
        String jdbcUrl = jdbcUrl(dataset, scope);
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("DuckDB JDBC driver is not on the classpath", e);
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            List<String> sourceFields = dataset.getSource() == null ? null : dataset.getSource().getFields();
            List<String> targetFields = dataset.getTarget() == null ? null : dataset.getTarget().getFields();

            long sourceCount = loadSide(connection, "source_rows", sourceRows, sourceFields);
            long targetCount = loadSide(connection, "target_rows", targetRows, targetFields);

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE OR REPLACE TABLE src_except AS "
                        + "SELECT * FROM source_rows EXCEPT ALL SELECT * FROM target_rows");
                statement.execute("CREATE OR REPLACE TABLE tgt_except AS "
                        + "SELECT * FROM target_rows EXCEPT ALL SELECT * FROM source_rows");
            }

            Map<String, String> srcExcept = loadExcept(connection, "src_except");
            Map<String, String> tgtExcept = loadExcept(connection, "tgt_except");

            long mismatched = 0;
            long sourceOnly = 0;
            long targetOnly = 0;
            List<RecRecordRepository.RecRecord> details = new ArrayList<>();
            ReconMode mode = settings.resolvedMode();
            List<String> conditions = settings.resolvedConditionFields();
            List<String> fieldNames = resolveFieldNames(dataset);

            Set<String> processed = new LinkedHashSet<>();
            for (String key : srcExcept.keySet()) {
                processed.add(key);
                String srcPayload = srcExcept.get(key);
                String tgtPayload = tgtExcept.get(key);
                RecRecordRepository.RecStatus status;
                if (tgtPayload == null) {
                    status = RecRecordRepository.RecStatus.SOURCE_ONLY;
                    sourceOnly++;
                } else {
                    status = RecRecordRepository.RecStatus.MISMATCHED;
                    mismatched++;
                }
                if (shouldPersistDetail(mode, status, srcPayload, tgtPayload, conditions, fieldNames)) {
                    details.add(new RecRecordRepository.RecRecord(
                            key,
                            hashPayload(srcPayload),
                            hashPayload(tgtPayload),
                            status,
                            fieldDiffs(mode, srcPayload, tgtPayload, conditions, fieldNames),
                            srcPayload,
                            tgtPayload
                    ));
                }
            }
            for (Map.Entry<String, String> entry : tgtExcept.entrySet()) {
                if (!processed.add(entry.getKey())) {
                    continue;
                }
                targetOnly++;
                if (shouldPersistDetail(
                        mode,
                        RecRecordRepository.RecStatus.TARGET_ONLY,
                        null,
                        entry.getValue(),
                        conditions,
                        fieldNames)) {
                    details.add(new RecRecordRepository.RecRecord(
                            entry.getKey(),
                            null,
                            hashPayload(entry.getValue()),
                            RecRecordRepository.RecStatus.TARGET_ONLY,
                            fieldDiffs(mode, null, entry.getValue(), conditions, fieldNames),
                            null,
                            entry.getValue()
                    ));
                }
            }

            long uniqueKeys = countUniqueKeys(connection);
            long matched = Math.max(0, uniqueKeys - mismatched - sourceOnly - targetOnly);

            return new Result(
                    sourceCount,
                    targetCount,
                    matched,
                    mismatched,
                    sourceOnly,
                    targetOnly,
                    details
            );
        } catch (SQLException e) {
            throw new IllegalStateException("DuckDB EXCEPT reconciliation failed", e);
        }
    }

    private String jdbcUrl(DatasetConfiguration dataset, RunScope scope) {
        try {
            Files.createDirectories(snapshotRoot);
            String safe = (dataset.getId() == null ? "run" : dataset.getId()).replaceAll("[^A-Za-z0-9._-]", "_");
            Path file = snapshotRoot.resolve(safe + ".duckdb");
            if (scope == RunScope.FULL && Files.exists(file)) {
                Files.deleteIfExists(file);
                Files.deleteIfExists(Path.of(file.toString() + ".wal"));
            }
            return "jdbc:duckdb:" + file.toAbsolutePath();
        } catch (Exception e) {
            return "jdbc:duckdb:";
        }
    }

    private static long loadSide(
            Connection connection,
            String table,
            Flux<DataLoadDefinition.RawRow> rows,
            List<String> fieldNames) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE TABLE " + table + " (migration_key VARCHAR, payload VARCHAR)");
        }

        DuckDBConnection duck = connection.unwrap(DuckDBConnection.class);
        AtomicLong count = new AtomicLong();
        try (DuckDBAppender appender = duck.createAppender("main", table)) {
            rows.doOnNext(row -> {
                try {
                    appender.beginRow();
                    appender.append(row.migrationKey());
                    appender.append(payload(row, fieldNames));
                    appender.endRow();
                    long n = count.incrementAndGet();
                    if (n % APPEND_FLUSH_EVERY == 0) {
                        appender.flush();
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("DuckDB append failed for table " + table, e);
                }
            }).then().block();
            appender.flush();
        }
        return count.get();
    }

    private static long countUniqueKeys(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT migration_key FROM source_rows
                    UNION
                    SELECT migration_key FROM target_rows
                ) keys
                """;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Map<String, String> loadExcept(Connection connection, String table) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT migration_key, payload FROM " + table)) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    static String payload(DataLoadDefinition.RawRow row, List<String> fieldNames) {
        try {
            return JSON.writeValueAsString(row.comparableValues());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode DuckDB row payload", e);
        }
    }

    static String payload(DataLoadDefinition.RawRow row) {
        return payload(row, null);
    }

    private static String hashPayload(String payload) {
        if (payload == null) {
            return null;
        }
        return RowHasher.hash(List.of(payload), HashingStrategy.TypeStrict);
    }

    private static List<String> resolveFieldNames(DatasetConfiguration dataset) {
        if (dataset.getSource() != null
                && dataset.getSource().getFields() != null
                && !dataset.getSource().getFields().isEmpty()) {
            return dataset.getSource().getFields();
        }
        if (dataset.getTarget() != null
                && dataset.getTarget().getFields() != null
                && !dataset.getTarget().getFields().isEmpty()) {
            return dataset.getTarget().getFields();
        }
        return List.of();
    }

    private static boolean shouldPersistDetail(
            ReconMode mode,
            RecRecordRepository.RecStatus status,
            String sourcePayload,
            String targetPayload,
            List<String> conditions,
            List<String> fieldNames) {
        if (mode == ReconMode.COUNTS) {
            return false;
        }
        if (status == RecRecordRepository.RecStatus.MATCHED) {
            return false;
        }
        if (mode == ReconMode.MISMATCH_DETAILS
                && status == RecRecordRepository.RecStatus.MISMATCHED
                && conditions != null
                && !conditions.isEmpty()
                && !conditionMismatch(sourcePayload, targetPayload, conditions, fieldNames)) {
            return false;
        }
        return true;
    }

    private static boolean conditionMismatch(
            String sourcePayload,
            String targetPayload,
            List<String> conditions,
            List<String> fieldNames) {
        List<Object> left = readList(sourcePayload);
        List<Object> right = readList(targetPayload);
        List<String> names = fieldNames == null || fieldNames.isEmpty() ? conditions : fieldNames;
        for (String field : conditions) {
            int index = names.indexOf(field);
            if (index < 0) {
                return true;
            }
            Object l = index < left.size() ? left.get(index) : null;
            Object r = index < right.size() ? right.get(index) : null;
            if (!Objects.equals(l, r)) {
                return true;
            }
        }
        return false;
    }

    private static String fieldDiffs(
            ReconMode mode,
            String sourcePayload,
            String targetPayload,
            List<String> conditions,
            List<String> fieldNames) {
        if (mode != ReconMode.FIELD_DETAILS) {
            return null;
        }
        List<Object> left = readList(sourcePayload);
        List<Object> right = readList(targetPayload);
        List<String> fields = conditions != null && !conditions.isEmpty()
                ? conditions
                : (fieldNames == null || fieldNames.isEmpty()
                        ? indexedNames(Math.max(left.size(), right.size()))
                        : fieldNames);
        LinkedHashMap<String, String> diffs = new LinkedHashMap<>();
        List<String> indexNames = fieldNames == null || fieldNames.isEmpty() ? fields : fieldNames;
        for (String field : fields) {
            int index = indexNames.indexOf(field);
            Object l = index >= 0 && index < left.size() ? left.get(index) : null;
            Object r = index >= 0 && index < right.size() ? right.get(index) : null;
            String status;
            if (l == null && r == null) {
                status = RecRecordRepository.RecStatus.MATCHED.name();
            } else if (l == null) {
                status = RecRecordRepository.RecStatus.TARGET_ONLY.name();
            } else if (r == null) {
                status = RecRecordRepository.RecStatus.SOURCE_ONLY.name();
            } else if (Objects.equals(l, r)) {
                status = RecRecordRepository.RecStatus.MATCHED.name();
            } else {
                status = RecRecordRepository.RecStatus.MISMATCHED.name();
            }
            diffs.put(field, status);
        }
        return com.mms.data.recon.recrun.FieldDiffs.toJson(diffs);
    }

    private static List<String> indexedNames(int size) {
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add("c" + i);
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readList(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(payload, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public record Result(
            long sourceCount,
            long targetCount,
            long matched,
            long mismatched,
            long sourceOnly,
            long targetOnly,
            List<RecRecordRepository.RecRecord> details) {

        public RecRunRepository.RunSummary summary() {
            return new RecRunRepository.RunSummary(
                    sourceCount, targetCount, matched, mismatched, sourceOnly, targetOnly
            );
        }
    }
}
