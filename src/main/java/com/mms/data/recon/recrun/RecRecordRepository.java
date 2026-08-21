package com.mms.data.recon.recrun;

import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.config.ReconDatabaseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecRecordRepository {

    private final DataSource dataSource;
    private final String recordTable;
    private final String createdBy;

    public RecRecordRepository(DataSource dataSource) {
        this(dataSource, new ReconDatabaseProperties(), null);
    }

    @Autowired
    public RecRecordRepository(
            DataSource dataSource,
            ReconDatabaseProperties database,
            RecConfiguration configuration) {
        this.dataSource = dataSource;
        this.recordTable = database.qualifiedRecordTable();
        this.createdBy = configuration == null ? "data-recon" : configuration.getActor();
    }

    public void insertBatch(long runId, List<RecRecord> records) {
        String sql = """
                INSERT INTO %s
                    (run_id, migration_key, source_hash, target_hash, status, field_diffs,
                     source_payload, target_payload, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)
                """.formatted(recordTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int queued = 0;
            for (RecRecord r : records) {
                ps.setLong(1, runId);
                ps.setString(2, r.migrationKey());
                ps.setString(3, r.sourceHash());
                ps.setString(4, r.targetHash());
                ps.setString(5, r.status().name());
                ps.setString(6, r.fieldDiffs());
                ps.setString(7, r.sourcePayload());
                ps.setString(8, r.targetPayload());
                ps.setString(9, createdBy);
                ps.addBatch();
                if (++queued >= 1000) {
                    ps.executeBatch();
                    queued = 0;
                }
            }
            if (queued > 0) {
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to persist reconciliation records", e);
        }
    }

    public List<RecRecord> findByRun(long runId, String status) {
        return findByRun(runId, status, Integer.MAX_VALUE, 0).records();
    }

    public Page findByRun(long runId, String status, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        int safeOffset = Math.max(0, offset);
        String normalized = normalizeStatus(status);

        String countSql = """
                SELECT COUNT(*)
                FROM %s
                WHERE run_id = ?
                  AND (? IS NULL OR status = ?)
                """.formatted(recordTable);

        String pageSql = """
                SELECT migration_key, source_hash, target_hash, status, field_diffs, source_payload, target_payload
                FROM %s
                WHERE run_id = ?
                  AND (? IS NULL OR status = ?)
                ORDER BY migration_key
                LIMIT ? OFFSET ?
                """.formatted(recordTable);

        try (Connection c = dataSource.getConnection()) {
            long total;
            try (PreparedStatement countPs = c.prepareStatement(countSql)) {
                countPs.setLong(1, runId);
                countPs.setString(2, normalized);
                countPs.setString(3, normalized);
                try (ResultSet rs = countPs.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }

            List<RecRecord> out = new ArrayList<>();
            try (PreparedStatement pagePs = c.prepareStatement(pageSql)) {
                pagePs.setLong(1, runId);
                pagePs.setString(2, normalized);
                pagePs.setString(3, normalized);
                pagePs.setInt(4, safeLimit);
                pagePs.setInt(5, safeOffset);
                try (ResultSet rs = pagePs.executeQuery()) {
                    while (rs.next()) {
                        out.add(new RecRecord(
                                rs.getString("migration_key"),
                                rs.getString("source_hash"),
                                rs.getString("target_hash"),
                                RecStatus.valueOf(rs.getString("status")),
                                rs.getString("field_diffs"),
                                rs.getString("source_payload"),
                                rs.getString("target_payload")
                        ));
                    }
                }
            }
            return new Page(runId, normalized, safeLimit, safeOffset, total, out.size(), List.copyOf(out));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read reconciliation records", e);
        }
    }

    /**
     * Accepts stored statuses and short aliases: {@code SOURCE}→{@code SOURCE_ONLY},
     * {@code TARGET}→{@code TARGET_ONLY}.
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String trimmed = status.trim();
        if ("SOURCE".equalsIgnoreCase(trimmed)) {
            return RecStatus.SOURCE_ONLY.name();
        }
        if ("TARGET".equalsIgnoreCase(trimmed)) {
            return RecStatus.TARGET_ONLY.name();
        }
        for (RecStatus value : RecStatus.values()) {
            if (value.name().equalsIgnoreCase(trimmed)) {
                return value.name();
            }
        }
        throw new IllegalArgumentException(
                "Unknown status '" + status + "'; use MATCHED, MISMATCHED, SOURCE_ONLY|SOURCE, TARGET_ONLY|TARGET"
        );
    }

    public record Page(
            long runId,
            String status,
            int limit,
            int offset,
            long total,
            int pageSize,
            List<RecRecord> records) {}

    public record RecRecord(
            String migrationKey,
            String sourceHash,
            String targetHash,
            RecStatus status,
            String fieldDiffs,
            String sourcePayload,
            String targetPayload) {

        public RecRecord(String migrationKey, String sourceHash, String targetHash, RecStatus status) {
            this(migrationKey, sourceHash, targetHash, status, null, null, null);
        }

        public RecRecord(
                String migrationKey,
                String sourceHash,
                String targetHash,
                RecStatus status,
                String fieldDiffs) {
            this(migrationKey, sourceHash, targetHash, status, fieldDiffs, null, null);
        }
    }

    public enum RecStatus {
        MATCHED,
        MISMATCHED,
        SOURCE_ONLY,
        TARGET_ONLY
    }
}
