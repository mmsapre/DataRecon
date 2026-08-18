package com.mms.data.recon.config;

public class BigQueryDatasourceProperties {

    private String name;
    private String jdbcUrl;
    private String driverClassName = "com.simba.googlebigquery.jdbc.Driver";
    private String projectId;
    private String dataset;
    private String catalog;
    private String username;
    private String password;
    private String credentialsFile;
    private int oauthType = 3;
    private int maxSize = 5;
    private String calciteSchema = "bq";

    public BigQueryDatasourceProperties() {}

    public BigQueryDatasourceProperties(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCredentialsFile() { return credentialsFile; }
    public void setCredentialsFile(String credentialsFile) { this.credentialsFile = credentialsFile; }

    public int getOauthType() { return oauthType; }
    public void setOauthType(int oauthType) { this.oauthType = oauthType; }

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

    public String getCalciteSchema() { return calciteSchema; }
    public void setCalciteSchema(String calciteSchema) { this.calciteSchema = calciteSchema; }

    public String resolveCatalog() {
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        return projectId;
    }

    public String resolveCalciteSchema() {
        return calciteSchema == null || calciteSchema.isBlank() ? "bq" : calciteSchema;
    }

    public String resolveJdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException(
                    "BigQuery datasource [" + name + "] requires jdbc-url or project-id"
            );
        }
        StringBuilder url = new StringBuilder("jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443")
                .append(";ProjectId=").append(projectId);
        if (credentialsFile != null && !credentialsFile.isBlank()) {
            url.append(";OAuthType=0")
                    .append(";OAuthPvtKeyPath=").append(credentialsFile);
        } else {
            url.append(";OAuthType=").append(oauthType);
        }
        return url.toString();
    }
}
