package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(MmsRecon.PREFIX + ".database")
public class ReconDatabaseProperties {

    private String host = "localhost";
    private int port = 5436;
    private String name = "data_recon";
    private String username = "postgres";
    private String password = "postgres";
    private int maximumPoolSize = 5;
    private String schema = "public";
    private Tables tables = new Tables();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = SqlIdentifiers.require("schema", schema); }

    public Tables getTables() { return tables; }
    public void setTables(Tables tables) { this.tables = tables == null ? new Tables() : tables; }

    public String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s?currentSchema=%s".formatted(
                host, port, name, resolvedSchema()
        );
    }

    public String qualifiedRunTable() {
        return SqlIdentifiers.qualifyTable(resolvedSchema(), tables.getRun());
    }

    public String qualifiedRecordTable() {
        return SqlIdentifiers.qualifyTable(resolvedSchema(), tables.getRecord());
    }

    public String qualifiedDatasourceTable() {
        return SqlIdentifiers.qualifyTable(resolvedSchema(), tables.getDatasource());
    }

    public String qualifiedDomainTable() {
        return SqlIdentifiers.qualifyTable(resolvedSchema(), tables.getDomain());
    }

    public String qualifiedProfileTable() {
        return SqlIdentifiers.qualifyTable(resolvedSchema(), tables.getProfile());
    }

    public String resolvedSchema() {
        return schema == null || schema.isBlank() ? "public" : schema;
    }

    public static class Tables {
        private String run = "rec_run";
        private String record = "rec_record";
        private String datasource = "rec_datasource";
        private String domain = "rec_domain";
        private String profile = "rec_profile";

        public String getRun() { return run; }
        public void setRun(String run) { this.run = SqlIdentifiers.require("tables.run", run); }

        public String getRecord() { return record; }
        public void setRecord(String record) { this.record = SqlIdentifiers.require("tables.record", record); }

        public String getDatasource() { return datasource; }
        public void setDatasource(String datasource) {
            this.datasource = SqlIdentifiers.require("tables.datasource", datasource);
        }

        public String getDomain() { return domain; }
        public void setDomain(String domain) {
            this.domain = SqlIdentifiers.require("tables.domain", domain);
        }

        public String getProfile() { return profile; }
        public void setProfile(String profile) {
            this.profile = SqlIdentifiers.require("tables.profile", profile);
        }
    }
}
