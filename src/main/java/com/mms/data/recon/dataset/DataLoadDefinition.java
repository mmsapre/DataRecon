package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.SqlIdentifiers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Source or target load. Prefer {@code schema}/{@code table}/{@code fields} (SQL is generated)
 * or, when you need joins/filters, set {@code query} directly:
 * PostgreSQL and BigQuery use SQL that aliases the key as {@code MigrationKey};
 * MongoDB uses a JSON filter (still set {@code collection} and {@code fields}).
 * {@code query} wins over generated SQL. {@code queryFile} is used only when {@code query} is blank.
 * Optional {@code queryParams} binds positional {@code ?} placeholders (SQL prepared statements;
 * Mongo JSON {@code "?"} / {@code ?} placeholders).
 */
public class DataLoadDefinition {

    public static final String MIGRATION_KEY_COLUMN_NAME = "MigrationKey";

    private String datasource;
    private String datasourceRef;
    private DatasourceType type;
    private String query;
    private Path queryFile;
    private List<Object> queryParams;
    private String collection;
    private String schema;
    private String table;
    private MigrationKeySpec migrationKey;
    private List<String> fields;

    private transient String datasetId;
    private transient Role role;
    private transient Path queryFileBaseDir = Path.of("queries");

    public enum Role { SOURCE, TARGET }

    public String getDatasource() { return firstNonBlank(datasource, datasourceRef); }
    public void setDatasource(String datasource) { this.datasource = datasource; }

    public String getDatasourceRef() { return getDatasource(); }
    public void setDatasourceRef(String datasourceRef) { this.datasourceRef = datasourceRef; }

    public DatasourceType getType() { return type; }
    public void setType(DatasourceType type) { this.type = type; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Path getQueryFile() { return queryFile; }
    public void setQueryFile(Path queryFile) { this.queryFile = queryFile; }

    public List<Object> getQueryParams() { return queryParams; }
    public void setQueryParams(List<Object> queryParams) {
        this.queryParams = queryParams == null ? null : new java.util.ArrayList<>(queryParams);
    }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public MigrationKeySpec getMigrationKey() { return migrationKey; }

    public void setMigrationKey(MigrationKeySpec migrationKey) {
        this.migrationKey = migrationKey;
    }

    public void setMigrationKey(String column) {
        this.migrationKey = blank(column) ? null : MigrationKeySpec.single(column);
    }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public String getDatasetId() { return datasetId; }
    public Role getRole() { return role; }

    public void initialize(String datasetId, Role role, Path queryFileBaseDir) {
        this.datasetId = datasetId;
        this.role = role;
        this.queryFileBaseDir = queryFileBaseDir == null ? Path.of("queries") : queryFileBaseDir;
    }

    public void applyDatasource(String name) {
        if (blank(getDatasource()) && !blank(name)) {
            this.datasource = name;
        }
    }

    public void attachDatasource(String name) {
        if (blank(name)) {
            return;
        }
        this.datasource = name;
        this.datasourceRef = name;
    }

    public void applyMigrationKey(MigrationKeySpec spec) {
        if (spec != null) {
            this.migrationKey = spec;
        }
    }

    public DatasourceType resolveType(DatasourceCatalog catalog) {
        if (catalog == null) {
            return resolveType((Function<String, Optional<DatasourceType>>) null);
        }
        return resolveType(catalog::typeOf);
    }

    public DatasourceType resolveType(Function<String, Optional<DatasourceType>> catalogLookup) {
        if (type != null) {
            return type;
        }
        String ref = getDatasource();
        if (ref != null && catalogLookup != null) {
            Optional<DatasourceType> fromCatalog = catalogLookup.apply(ref);
            if (fromCatalog != null && fromCatalog.isPresent()) {
                return fromCatalog.get();
            }
        }
        if (collection != null && !collection.isBlank()) {
            return DatasourceType.mongo;
        }
        if (ref != null && catalogLookup != null) {
            Optional<DatasourceType> missing = catalogLookup.apply(ref);
            if (missing != null && missing.isEmpty()) {
                throw new ConfigurationException(
                        "Unknown datasource [" + ref + "] for dataset " + datasetId
                                + ". Define it under mms.recon.postgres|mongodb|bigquery|file.datasources "
                                + "and attach it to the profile source/target."
                );
            }
        }
        return DatasourceType.postgres;
    }

    public String describeType() {
        if (type != null) {
            return type.name();
        }
        if (collection != null && !collection.isBlank()) {
            return DatasourceType.mongo.name();
        }
        return DatasourceType.postgres.name();
    }

    public String describeType(DatasourceCatalog catalog) {
        return resolveType(catalog).name();
    }

    public String migrationKeyField() {
        MigrationKeySpec spec = migrationKey;
        if (spec == null) {
            return MIGRATION_KEY_COLUMN_NAME;
        }
        List<String> columns = spec.resolvedColumns();
        return columns.isEmpty() ? MIGRATION_KEY_COLUMN_NAME : columns.get(0);
    }

    public String resolveQueryStatement() {
        return resolveQueryStatement(resolveType((Function<String, Optional<DatasourceType>>) null));
    }

    public String resolveQueryStatement(DatasourceType resolvedType) {
        if (query != null && !query.isBlank()) {
            return query.trim();
        }

        if (resolvedType != DatasourceType.mongo && table != null && !table.isBlank()) {
            return generateSelect(resolvedType);
        }

        Path file = queryFile;
        if (file == null && resolvedType == DatasourceType.mongo) {
            Path json = queryFileBaseDir.resolve(defaultFileName(".json"));
            if (Files.exists(json)) {
                file = json;
            } else {
                return "{}";
            }
        }

        if (file == null) {
            file = queryFileBaseDir.resolve(defaultFileName(".sql"));
        }

        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigurationException("Unable to read query file " + file, e);
        }
    }

    /**
     * Query text stored on {@code rec_run}: inline {@code query} if set, generated SQL from
     * table mapping, Mongo {@code {}}, or null when the side has no resolvable statement.
     */
    public String storedQueryStatement() {
        if (query != null && !query.isBlank()) {
            return query.trim();
        }
        DatasourceType type = resolveType((Function<String, Optional<DatasourceType>>) null);
        if (type == DatasourceType.mongo) {
            try {
                return resolveQueryStatement(DatasourceType.mongo);
            } catch (RuntimeException e) {
                return "{}";
            }
        }
        if (table != null && !table.isBlank()) {
            try {
                return generateSelect(type);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    String generateSelect(DatasourceType resolvedType) {
        MigrationKeySpec key = migrationKey;
        if (key == null) {
            throw new ConfigurationException(
                    "Dataset " + datasetId + " " + role
                            + " uses table [" + qualifiedRelation() + "] and must set `migrationKey` "
                            + "(type SINGLE, COMPOSITE, or DEFINED)"
            );
        }
        if (fields == null || fields.isEmpty()) {
            throw new ConfigurationException(
                    "Dataset " + datasetId + " " + role
                            + " uses table [" + qualifiedRelation() + "] and must set `fields` "
                            + "in comparable-column order"
            );
        }
        key.initialize();
        String alias = resolvedType == DatasourceType.bigquery
                ? " AS " + MIGRATION_KEY_COLUMN_NAME
                : " AS \"" + MIGRATION_KEY_COLUMN_NAME + "\"";
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(key.sqlExpression(resolvedType))
                .append(alias);
        for (String field : fields) {
            sql.append(", ").append(SqlIdentifiers.require("fields", field));
        }
        sql.append(" FROM ").append(qualifiedRelation());
        return sql.toString();
    }

    public String qualifiedRelation() {
        return SqlIdentifiers.qualifyTable(schema, table);
    }

    private String defaultFileName(String extension) {
        return datasetId + "-" + role.name().toLowerCase() + extension;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (!blank(primary)) {
            return primary;
        }
        return blank(fallback) ? null : fallback;
    }

    public record RawRow(List<String> columns, List<Object> values) {
        public int migrationKeyIndex() {
            int found = -1;
            for (int i = 0; i < columns.size(); i++) {
                if (MIGRATION_KEY_COLUMN_NAME.equalsIgnoreCase(columns.get(i))) {
                    if (found >= 0) {
                        throw new IllegalArgumentException(
                                "More than one column named " + MIGRATION_KEY_COLUMN_NAME + " found in dataset"
                        );
                    }
                    found = i;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException(
                        "No column named " + MIGRATION_KEY_COLUMN_NAME + " found in dataset"
                );
            }
            return found;
        }

        public String migrationKey() {
            Object value = values.get(migrationKeyIndex());
            if (value == null) {
                throw new IllegalArgumentException("MigrationKey has null value somewhere in dataset");
            }
            return String.valueOf(value);
        }

        public List<Object> comparableValues() {
            int keyIndex = migrationKeyIndex();
            List<Object> result = new java.util.ArrayList<>(values);
            result.remove(keyIndex);
            return result;
        }
    }
}
