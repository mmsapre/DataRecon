package com.mms.data.recon.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@EachProperty(MmsRecon.PREFIX + ".postgres.datasources")
public class PostgresDatasourceProperties {

    private final String name;
    private String url;
    private String host = "localhost";
    private int port = 5432;
    private String database = "data";
    private String username = "postgres";
    private String password = "postgres";
    private int maxSize = 10;

    public PostgresDatasourceProperties(@Parameter String name) {
        this.name = name;
    }

    public String getName() { return name; }

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
