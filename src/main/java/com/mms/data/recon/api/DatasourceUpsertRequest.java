package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.dataset.DatasourceType;

import java.util.List;

/**
 * Create/update a named source or target datasource before attaching it to a profile.
 */
public class DatasourceUpsertRequest {

    private String name;
    private DatasourceType type;
    private List<String> tags;

    // postgres
    private String url;
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;
    private Integer maxSize;

    // mongo
    private String uri;
    private String authDatabase;

    // bigquery
    private String jdbcUrl;
    private String driverClassName;
    private String projectId;
    private String dataset;
    private String catalog;
    private String credentialsFile;
    private Integer oauthType;
    private String calciteSchema;

    // file
    private String path;
    private String pattern;
    private String format;
    private String table;
    private String sheet;
    private String delimiter;
    private Boolean header;

    public PostgresDatasourceProperties toPostgres(String name) {
        PostgresDatasourceProperties props = new PostgresDatasourceProperties(name);
        if (url != null) {
            props.setUrl(url);
        }
        if (host != null) {
            props.setHost(host);
        }
        if (port != null) {
            props.setPort(port);
        }
        if (database != null) {
            props.setDatabase(database);
        }
        if (username != null) {
            props.setUsername(username);
        }
        if (password != null) {
            props.setPassword(password);
        }
        if (maxSize != null) {
            props.setMaxSize(maxSize);
        }
        return props;
    }

    public MongoDatasourceProperties toMongo(String name) {
        MongoDatasourceProperties props = new MongoDatasourceProperties(name);
        if (uri != null) {
            props.setUri(uri);
        }
        if (database != null) {
            props.setDatabase(database);
        }
        if (username != null) {
            props.setUsername(username);
        }
        if (password != null) {
            props.setPassword(password);
        }
        if (authDatabase != null) {
            props.setAuthDatabase(authDatabase);
        }
        return props;
    }

    public BigQueryDatasourceProperties toBigQuery(String name) {
        BigQueryDatasourceProperties props = new BigQueryDatasourceProperties(name);
        if (jdbcUrl != null) {
            props.setJdbcUrl(jdbcUrl);
        }
        if (driverClassName != null) {
            props.setDriverClassName(driverClassName);
        }
        if (projectId != null) {
            props.setProjectId(projectId);
        }
        if (dataset != null) {
            props.setDataset(dataset);
        }
        if (catalog != null) {
            props.setCatalog(catalog);
        }
        if (username != null) {
            props.setUsername(username);
        }
        if (password != null) {
            props.setPassword(password);
        }
        if (credentialsFile != null) {
            props.setCredentialsFile(credentialsFile);
        }
        if (oauthType != null) {
            props.setOauthType(oauthType);
        }
        if (maxSize != null) {
            props.setMaxSize(maxSize);
        }
        if (calciteSchema != null) {
            props.setCalciteSchema(calciteSchema);
        }
        return props;
    }

    public FileDatasourceProperties toFile(String name) {
        FileDatasourceProperties props = new FileDatasourceProperties(name);
        if (path != null) {
            props.setPath(path);
        }
        if (pattern != null) {
            props.setPattern(pattern);
        }
        if (format != null) {
            props.setFormat(format);
        }
        if (table != null) {
            props.setTable(table);
        }
        if (sheet != null) {
            props.setSheet(sheet);
        }
        if (delimiter != null) {
            props.setDelimiter(delimiter);
        }
        if (header != null) {
            props.setHeader(header);
        }
        if (calciteSchema != null) {
            props.setCalciteSchema(calciteSchema);
        }
        return props;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DatasourceType getType() { return type; }
    public void setType(DatasourceType type) { this.type = type; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getMaxSize() { return maxSize; }
    public void setMaxSize(Integer maxSize) { this.maxSize = maxSize; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getAuthDatabase() { return authDatabase; }
    public void setAuthDatabase(String authDatabase) { this.authDatabase = authDatabase; }

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

    public String getCredentialsFile() { return credentialsFile; }
    public void setCredentialsFile(String credentialsFile) { this.credentialsFile = credentialsFile; }

    public Integer getOauthType() { return oauthType; }
    public void setOauthType(Integer oauthType) { this.oauthType = oauthType; }

    public String getCalciteSchema() { return calciteSchema; }
    public void setCalciteSchema(String calciteSchema) { this.calciteSchema = calciteSchema; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

    public Boolean getHeader() { return header; }
    public void setHeader(Boolean header) { this.header = header; }
}
