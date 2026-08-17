package com.mms.data.recon.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@EachProperty(MmsRecon.PREFIX + ".mongodb.datasources")
public class MongoDatasourceProperties {

    private final String name;
    private String uri = "mongodb://localhost:27017";
    private String database = "data";
    private String username;
    private String password;
    private String authDatabase = "admin";

    public MongoDatasourceProperties(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthDatabase() {
        return authDatabase;
    }

    public void setAuthDatabase(String authDatabase) {
        this.authDatabase = authDatabase;
    }
}
