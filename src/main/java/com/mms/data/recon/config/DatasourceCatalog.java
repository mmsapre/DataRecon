package com.mms.data.recon.config;

import com.mms.data.recon.dataset.DatasourceType;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class DatasourceCatalog {

    private final Map<String, DatasourceType> types;

    @Autowired
    public DatasourceCatalog(
            PostgresDatasourcesProperties postgres,
            MongoDatasourcesProperties mongo,
            BigQueryDatasourcesProperties bigquery,
            FileDatasourcesProperties files) {
        this(postgres.asList(), mongo.asList(), bigquery.asList(), files.asList());
    }

    public DatasourceCatalog(
            @Nullable List<PostgresDatasourceProperties> postgres,
            @Nullable List<MongoDatasourceProperties> mongo,
            @Nullable List<BigQueryDatasourceProperties> bigquery,
            @Nullable List<FileDatasourceProperties> files) {
        Map<String, DatasourceType> map = new LinkedHashMap<>();
        addAll(map, postgres, DatasourceType.postgres);
        addAll(map, mongo, DatasourceType.mongo);
        addAll(map, bigquery, DatasourceType.bigquery);
        addAll(map, files, DatasourceType.file);
        this.types = Map.copyOf(map);
    }

    public Optional<DatasourceType> typeOf(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.get(datasourceRef));
    }

    public boolean has(String datasourceRef) {
        return typeOf(datasourceRef).isPresent();
    }

    public DatasourceType require(String datasourceRef) {
        return typeOf(datasourceRef).orElseThrow(() -> new ConfigurationException(
                "Unknown datasourceRef [" + datasourceRef + "]. Known datasources: " + names()
        ));
    }

    public Set<String> names() {
        return types.keySet();
    }

    public Map<String, DatasourceType> asMap() {
        return types;
    }

    private static void addAll(
            Map<String, DatasourceType> map,
            List<?> properties,
            DatasourceType type) {
        if (properties == null) {
            return;
        }
        for (Object property : properties) {
            String name = nameOf(property);
            DatasourceType existing = map.put(name, type);
            if (existing != null && existing != type) {
                throw new ConfigurationException(
                        "Datasource [" + name + "] is configured as both " + existing + " and " + type
                );
            }
        }
    }

    private static String nameOf(Object property) {
        if (property instanceof PostgresDatasourceProperties postgres) {
            return postgres.getName();
        }
        if (property instanceof MongoDatasourceProperties mongo) {
            return mongo.getName();
        }
        if (property instanceof BigQueryDatasourceProperties bigquery) {
            return bigquery.getName();
        }
        if (property instanceof FileDatasourceProperties file) {
            return file.getName();
        }
        throw new IllegalArgumentException("Unsupported datasource property: " + property);
    }
}
