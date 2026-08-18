package com.mms.data.recon.config;

public class PostgresDatasourceProperties {

    private String name;
    private String url;
    private String host = "localhost";
    private int port = 5432;
    private String database = "data";
    private String username = "postgres";
    private String password = "postgres";
    private int maxSize = 10;

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
}
