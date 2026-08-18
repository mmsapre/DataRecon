package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(MmsRecon.PREFIX + ".postgres")
public class PostgresDatasourcesProperties {

    private Map<String, PostgresDatasourceProperties> datasources = new LinkedHashMap<>();

    public Map<String, PostgresDatasourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, PostgresDatasourceProperties> datasources) {
        this.datasources = datasources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datasources);
        this.datasources.forEach((name, props) -> {
            if (props != null) {
                props.setName(name);
            }
        });
    }

    public List<PostgresDatasourceProperties> asList() {
        return new ArrayList<>(datasources.values());
    }
}
