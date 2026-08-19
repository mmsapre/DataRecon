package com.mms.data.recon.config;

import java.util.List;

public class PostgresDatasourceProperties {

    private String name;
    private String url;
    private String host = "localhost";
    private int port = 5432;
    private String database = "data";
    private String username = "postgres";
    private String password = "postgres";
    private int maxSize = 10;
    /** Default Postgres schema for profiles that attach this datasource (overridable per side). */
    private String schema;
    private List<String> tags = List.of();

    public PostgresDatasourceProperties() {}

    public PostgresDatasourceProperties(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = Tags.copy(tags); }

    /** Prefer explicit schema; otherwise parse from connection URL/URI. */
    public String resolveSchema() {
        if (schema != null && !schema.isBlank()) {
            return schema.trim();
        }
        return PostgresConnectionFactoryCatalog.schemaFromUrl(resolveUrl());
    }

    /** Connection URI: {@code url} field (R2DBC or postgres URL). */
    public String resolveUrl() {
        return url == null || url.isBlank() ? null : url.trim();
    }
}
