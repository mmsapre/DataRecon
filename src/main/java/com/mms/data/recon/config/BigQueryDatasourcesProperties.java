package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(MmsRecon.PREFIX + ".bigquery")
public class BigQueryDatasourcesProperties {

    private Map<String, BigQueryDatasourceProperties> datasources = new LinkedHashMap<>();

    public Map<String, BigQueryDatasourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, BigQueryDatasourceProperties> datasources) {
        this.datasources = datasources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datasources);
        this.datasources.forEach((name, props) -> {
            if (props != null) {
                props.setName(name);
            }
        });
    }

    public List<BigQueryDatasourceProperties> asList() {
        return new ArrayList<>(datasources.values());
    }
}
