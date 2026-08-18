package com.mms.data.recon.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mms.data.recon.config.CatalogAudit;
import com.mms.data.recon.config.ReconDatabaseProperties;
import com.mms.data.recon.config.Tags;
import com.mms.data.recon.dataset.DatasourceType;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Versioned persistence for datasources, domains, and profiles.
 * Updates deactivate the previous active row and insert a new version.
 */
@Component
public class CatalogRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final String datasourceTable;
    private final String domainTable;
    private final String profileTable;

    public CatalogRepository(
            DataSource dataSource,
            ObjectMapper objectMapper,
            ReconDatabaseProperties database) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.datasourceTable = database.qualifiedDatasourceTable();
        this.domainTable = database.qualifiedDomainTable();
        this.profileTable = database.qualifiedProfileTable();
    }

    public void insertDatasource(
            String name,
            DatasourceType type,
            List<String> tags,
            DatasourceUpsertRequest config,
            CatalogAudit audit) {
        String sql = """
                INSERT INTO %s(name, type, tags_json, config_json, version, active,
                               created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(datasourceTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type.name());
            ps.setString(3, writeJson(Tags.copy(tags)));
            ps.setString(4, writeJson(config));
            bindAudit(ps, 5, audit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to persist datasource " + name, e);
        }
    }

    public void deactivateDatasource(String name, CatalogAudit audit) {
        deactivate(
                "UPDATE %s SET active = false, updated_at = ?, updated_by = ? WHERE name = ? AND active = true"
                        .formatted(datasourceTable),
                audit,
                name
        );
    }

    public List<StoredDatasource> findActiveDatasources() {
        String sql = """
                SELECT name, type, tags_json, config_json, version, active,
                       created_at, created_by, updated_at, updated_by
                FROM %s WHERE active = true ORDER BY name
                """.formatted(datasourceTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StoredDatasource> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new StoredDatasource(
                        rs.getString("name"),
                        DatasourceType.valueOf(rs.getString("type")),
                        readTags(rs.getString("tags_json")),
                        read(rs.getString("config_json"), DatasourceUpsertRequest.class),
                        readAudit(rs)
                ));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load datasources", e);
        }
    }

    public void insertDomain(String domainId, List<String> tags, DomainUpsertRequest config, CatalogAudit audit) {
        String sql = """
                INSERT INTO %s(domain_id, tags_json, config_json, version, active,
                               created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(domainTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, domainId);
            ps.setString(2, writeJson(Tags.copy(tags)));
            ps.setString(3, writeJson(config));
            bindAudit(ps, 4, audit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to persist domain " + domainId, e);
        }
    }

    public void deactivateDomain(String domainId, CatalogAudit audit) {
        deactivate(
                "UPDATE %s SET active = false, updated_at = ?, updated_by = ? WHERE domain_id = ? AND active = true"
                        .formatted(domainTable),
                audit,
                domainId
        );
    }

    public List<StoredDomain> findActiveDomains() {
        String sql = """
                SELECT domain_id, tags_json, config_json, version, active,
                       created_at, created_by, updated_at, updated_by
                FROM %s WHERE active = true ORDER BY domain_id
                """.formatted(domainTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StoredDomain> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new StoredDomain(
                        rs.getString("domain_id"),
                        readTags(rs.getString("tags_json")),
                        read(rs.getString("config_json"), DomainUpsertRequest.class),
                        readAudit(rs)
                ));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load domains", e);
        }
    }

    public void insertProfile(
            String domainId,
            String profileId,
            List<String> tags,
            ProfileUpsertRequest config,
            CatalogAudit audit) {
        String sql = """
                INSERT INTO %s(domain_id, profile_id, tags_json, config_json, version, active,
                               created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(profileTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, domainId);
            ps.setString(2, profileId);
            ps.setString(3, writeJson(Tags.copy(tags)));
            ps.setString(4, writeJson(config));
            bindAudit(ps, 5, audit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to persist profile " + domainId + "." + profileId, e);
        }
    }

    public void deactivateProfile(String domainId, String profileId, CatalogAudit audit) {
        String sql = """
                UPDATE %s SET active = false, updated_at = ?, updated_by = ?
                WHERE domain_id = ? AND profile_id = ? AND active = true
                """.formatted(profileTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(audit.updatedAt()));
            ps.setString(2, audit.updatedBy());
            ps.setString(3, domainId);
            ps.setString(4, profileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to deactivate profile " + domainId + "." + profileId, e);
        }
    }

    public void deactivateProfilesForDomain(String domainId, CatalogAudit audit) {
        String sql = """
                UPDATE %s SET active = false, updated_at = ?, updated_by = ?
                WHERE domain_id = ? AND active = true
                """.formatted(profileTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(audit.updatedAt()));
            ps.setString(2, audit.updatedBy());
            ps.setString(3, domainId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to deactivate profiles for domain " + domainId, e);
        }
    }

    public List<StoredProfile> findActiveProfiles() {
        String sql = """
                SELECT domain_id, profile_id, tags_json, config_json, version, active,
                       created_at, created_by, updated_at, updated_by
                FROM %s WHERE active = true ORDER BY domain_id, profile_id
                """.formatted(profileTable);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StoredProfile> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new StoredProfile(
                        rs.getString("domain_id"),
                        rs.getString("profile_id"),
                        readTags(rs.getString("tags_json")),
                        read(rs.getString("config_json"), ProfileUpsertRequest.class),
                        readAudit(rs)
                ));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load profiles", e);
        }
    }

    private void deactivate(String sql, CatalogAudit audit, String key) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(audit.updatedAt()));
            ps.setString(2, audit.updatedBy());
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to deactivate catalog row", e);
        }
    }

    private static void bindAudit(PreparedStatement ps, int start, CatalogAudit audit) throws SQLException {
        ps.setInt(start, audit.version());
        ps.setBoolean(start + 1, audit.active());
        ps.setTimestamp(start + 2, Timestamp.from(audit.createdAt()));
        ps.setString(start + 3, audit.createdBy());
        ps.setTimestamp(start + 4, Timestamp.from(audit.updatedAt()));
        ps.setString(start + 5, audit.updatedBy());
    }

    private static CatalogAudit readAudit(ResultSet rs) throws SQLException {
        return new CatalogAudit(
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getString("updated_by"),
                rs.getBoolean("active"),
                rs.getInt("version")
        );
    }

    private static Instant toInstant(@Nullable Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }

    private List<String> readTags(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String[] tags = read(json, String[].class);
        return Tags.copy(tags == null ? List.of() : List.of(tags));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize catalog payload", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize catalog payload", e);
        }
    }

    public record StoredDatasource(
            String name,
            DatasourceType type,
            List<String> tags,
            DatasourceUpsertRequest config,
            CatalogAudit audit) {}

    public record StoredDomain(
            String domainId,
            List<String> tags,
            DomainUpsertRequest config,
            CatalogAudit audit) {}

    public record StoredProfile(
            String domainId,
            String profileId,
            List<String> tags,
            ProfileUpsertRequest config,
            CatalogAudit audit) {}
}
