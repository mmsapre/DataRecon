package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(MmsRecon.PREFIX + ".mongodb")
public class MongoDatasourcesProperties {

    private Map<String, MongoDatasourceProperties> datasources = new LinkedHashMap<>();

    public Map<String, MongoDatasourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, MongoDatasourceProperties> datasources) {
        this.datasources = datasources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datasources);
        this.datasources.forEach((name, props) -> {
            if (props != null) {
                props.setName(name);
            }
        });
    }

    public List<MongoDatasourceProperties> asList() {
        return new ArrayList<>(datasources.values());
    }
}
