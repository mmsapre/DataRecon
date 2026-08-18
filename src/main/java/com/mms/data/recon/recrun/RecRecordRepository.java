package com.mms.data.recon.recrun;

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

    public RecRecordRepository(DataSource dataSource) {
        this(dataSource, new ReconDatabaseProperties());
    }

    @Autowired
    public RecRecordRepository(DataSource dataSource, ReconDatabaseProperties database) {
        this.dataSource = dataSource;
        this.recordTable = database.qualifiedRecordTable();
    }

    public void insertBatch(long runId, List<RecRecord> records) {
        String sql = """
                INSERT INTO %s
                    (run_id, migration_key, source_hash, target_hash, status, field_diffs)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(recordTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (RecRecord r : records) {
                ps.setLong(1, runId);
                ps.setString(2, r.migrationKey());
                ps.setString(3, r.sourceHash());
                ps.setString(4, r.targetHash());
                ps.setString(5, r.status().name());
                ps.setString(6, r.fieldDiffs());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to persist reconciliation records", e);
        }
    }

    public List<RecRecord> findByRun(long runId, String status) {
        String sql = """
                SELECT migration_key, source_hash, target_hash, status, field_diffs
                FROM %s
                WHERE run_id = ?
                  AND (? IS NULL OR status = ?)
                ORDER BY migration_key
                """.formatted(recordTable);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, runId);
            ps.setString(2, status);
            ps.setString(3, status);

            try (ResultSet rs = ps.executeQuery()) {
                List<RecRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new RecRecord(
                            rs.getString("migration_key"),
                            rs.getString("source_hash"),
                            rs.getString("target_hash"),
                            RecStatus.valueOf(rs.getString("status")),
                            rs.getString("field_diffs")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read reconciliation records", e);
        }
    }

    public record RecRecord(
            String migrationKey,
            String sourceHash,
            String targetHash,
            RecStatus status,
            String fieldDiffs) {

        public RecRecord(String migrationKey, String sourceHash, String targetHash, RecStatus status) {
            this(migrationKey, sourceHash, targetHash, status, null);
        }
    }

    public enum RecStatus {
        MATCHED,
        MISMATCHED,
        SOURCE_ONLY,
        TARGET_ONLY
    }
}
