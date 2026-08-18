package com.mms.data.recon.recrun;

import com.mms.data.recon.config.ReconDatabaseProperties;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.ReconMode;
import com.mms.data.recon.util.ThrowableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecRunRepository {

    private final DataSource dataSource;
    private final String runTable;

    public RecRunRepository(DataSource dataSource) {
        this(dataSource, new ReconDatabaseProperties());
    }

    @Autowired
    public RecRunRepository(DataSource dataSource, ReconDatabaseProperties database) {
        this.dataSource = dataSource;
        this.runTable = database.qualifiedRunTable();
    }

    public long create(String datasetId) {
        return create(datasetId, null, null);
    }

    public long create(DatasetConfiguration profile, Long domainRunId) {
        ReconMode mode = profile == null ? null : profile.resolvedRecon().resolvedMode();
        return create(profile, domainRunId, mode);
    }

    public long create(DatasetConfiguration profile, Long domainRunId, ReconMode mode) {
        return create(profile, domainRunId, mode, null, null, null);
    }

    public long create(
            DatasetConfiguration profile,
            Long domainRunId,
            ReconMode mode,
            String sourceQuery,
            String targetQuery,
            List<String> conditionFields) {
        String domainId = profile.getDomainId() != null ? profile.getDomainId() : profile.getId();
        return create(
                domainId,
                profile.getProfileId(),
                domainRunId,
                mode,
                sourceQuery,
                targetQuery,
                conditionFields
        );
    }

    public long createDomainRun(String domainId) {
        return create(domainId, null, null, null);
    }

    public long create(String domainId, String profileId, Long domainRunId) {
        return create(domainId, profileId, domainRunId, null);
    }

    public long create(String domainId, String profileId, Long domainRunId, ReconMode mode) {
        return create(domainId, profileId, domainRunId, mode, null, null, null);
    }

    public long create(
            String domainId,
            String profileId,
            Long domainRunId,
            ReconMode mode,
            String sourceQuery,
            String targetQuery,
            List<String> conditionFields) {
        String datasetId = profileId == null || profileId.isBlank()
                ? domainId
                : DatasetConfiguration.qualifiedId(domainId, profileId);
        String sql = """
                INSERT INTO %s(dataset_id, domain_id, profile_id, domain_run_id, status, started_at, active,
                               recon_mode, source_query, target_query, condition_fields)
                VALUES (?, ?, ?, ?, 'RUNNING', now(), false, ?, ?, ?, ?)
                RETURNING id
                """.formatted(runTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, datasetId);
            ps.setString(2, domainId);
            ps.setString(3, blankToNull(profileId));
            if (domainRunId == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, domainRunId);
            }
            ps.setString(5, mode == null ? null : mode.name());
            ps.setString(6, blankToNull(sourceQuery));
            ps.setString(7, blankToNull(targetQuery));
            ps.setString(8, joinFields(conditionFields));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create reconciliation run", e);
        }
    }

    public void complete(long id, RunSummary summary) {
        String sql = """
                UPDATE %s
                   SET status = 'COMPLETED',
                       completed_at = now(),
                       source_count = ?,
                       target_count = ?,
                       matched_count = ?,
                       mismatched_count = ?,
                       source_only_count = ?,
                       target_only_count = ?
                 WHERE id = ?
                """.formatted(runTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, summary.sourceCount());
            ps.setLong(2, summary.targetCount());
            ps.setLong(3, summary.matched());
            ps.setLong(4, summary.mismatched());
            ps.setLong(5, summary.sourceOnly());
            ps.setLong(6, summary.targetOnly());
            ps.setLong(7, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to complete reconciliation run", e);
        }
        activate(id);
    }

    public void activate(long id) {
        RunView run = find(id);
        if (run == null) {
            return;
        }
        String sql = """
                UPDATE %s
                   SET active = false
                 WHERE domain_id = ?
                   AND profile_id IS NOT DISTINCT FROM ?
                   AND id <> ?
                   AND active = true
                """.formatted(runTable);
        String mark = "UPDATE %s SET active = true WHERE id = ?".formatted(runTable);
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, run.domainId());
                ps.setString(2, run.profileId());
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(mark)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to activate reconciliation run", e);
        }
    }

    public void fail(long id, Throwable error) {
        String sql = """
                UPDATE %s
                   SET status = 'FAILED',
                       completed_at = now(),
                       error_message = ?
                 WHERE id = ?
                """.formatted(runTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, error == null ? null : ThrowableUtils.extractFailureCause(error));
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to fail reconciliation run", e);
        }
    }

    public List<RunView> list(String datasetId) {
        return list(null, null, datasetId, null, null);
    }

    public List<RunView> listByDomain(String domainId) {
        return listByDomain(domainId, null);
    }

    public List<RunView> listByDomain(String domainId, Boolean active) {
        return list(domainId, null, null, null, active);
    }

    public List<RunView> listByProfile(String domainId, String profileId) {
        return listByProfile(domainId, profileId, null);
    }

    public List<RunView> listByProfile(String domainId, String profileId, Boolean active) {
        return list(domainId, profileId, null, null, active);
    }

    public List<RunView> listByDomainRun(long domainRunId) {
        return list(null, null, null, domainRunId, null);
    }

    public RunView find(long id) {
        String sql = selectSql() + " WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load reconciliation run", e);
        }
    }

    private List<RunView> list(String domainId, String profileId, String datasetId, Long domainRunId, Boolean active) {
        String sql = selectSql() + """
                 WHERE (? IS NULL OR domain_id = ?)
                   AND (? IS NULL OR profile_id = ?)
                   AND (? IS NULL OR dataset_id = ?)
                   AND (? IS NULL OR domain_run_id = ? OR id = ?)
                   AND (? IS NULL OR active = ?)
                 ORDER BY id DESC
                """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, domainId);
            ps.setString(2, domainId);
            ps.setString(3, profileId);
            ps.setString(4, profileId);
            ps.setString(5, datasetId);
            ps.setString(6, datasetId);
            if (domainRunId == null) {
                ps.setNull(7, Types.BIGINT);
                ps.setNull(8, Types.BIGINT);
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(7, domainRunId);
                ps.setLong(8, domainRunId);
                ps.setLong(9, domainRunId);
            }
            if (active == null) {
                ps.setNull(10, Types.BOOLEAN);
                ps.setNull(11, Types.BOOLEAN);
            } else {
                ps.setBoolean(10, active);
                ps.setBoolean(11, active);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<RunView> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to list reconciliation runs", e);
        }
    }

    private String selectSql() {
        return """
                SELECT id, dataset_id, domain_id, profile_id, domain_run_id, status, started_at, completed_at,
                       source_count, target_count, matched_count,
                       mismatched_count, source_only_count, target_only_count,
                       error_message, active, recon_mode, source_query, target_query, condition_fields
                  FROM %s
                """.formatted(runTable);
    }

    private RunView map(ResultSet rs) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        long domainRun = rs.getLong("domain_run_id");
        boolean domainRunNull = rs.wasNull();
        return new RunView(
                rs.getLong("id"),
                rs.getString("dataset_id"),
                rs.getString("domain_id"),
                rs.getString("profile_id"),
                domainRunNull ? null : domainRun,
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                completed == null ? null : completed.toInstant(),
                rs.getLong("source_count"),
                rs.getLong("target_count"),
                rs.getLong("matched_count"),
                rs.getLong("mismatched_count"),
                rs.getLong("source_only_count"),
                rs.getLong("target_only_count"),
                rs.getString("error_message"),
                rs.getBoolean("active"),
                rs.getString("recon_mode"),
                rs.getString("source_query"),
                rs.getString("target_query"),
                splitFields(rs.getString("condition_fields"))
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static String joinFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return String.join(",", fields);
    }

    static List<String> splitFields(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public record RunSummary(
            long sourceCount,
            long targetCount,
            long matched,
            long mismatched,
            long sourceOnly,
            long targetOnly) {

        public static RunSummary of(List<RunView> runs) {
            return new RunSummary(
                    runs.stream().mapToLong(RunView::sourceCount).sum(),
                    runs.stream().mapToLong(RunView::targetCount).sum(),
                    runs.stream().mapToLong(RunView::matched).sum(),
                    runs.stream().mapToLong(RunView::mismatched).sum(),
                    runs.stream().mapToLong(RunView::sourceOnly).sum(),
                    runs.stream().mapToLong(RunView::targetOnly).sum()
            );
        }
    }

    public record RunView(
            long id,
            String datasetId,
            String domainId,
            String profileId,
            Long domainRunId,
            String status,
            Instant startedAt,
            Instant completedAt,
            long sourceCount,
            long targetCount,
            long matched,
            long mismatched,
            long sourceOnly,
            long targetOnly,
            String errorMessage,
            boolean active,
            String reconMode,
            String sourceQuery,
            String targetQuery,
            List<String> conditionFields) {}
}
