package com.mms.data.recon.config;

import com.mms.data.recon.dataset.DatasourceType;
import io.micronaut.context.exceptions.ConfigurationException;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class DatasourceCatalog {

    private final Map<String, DatasourceType> types;

    public DatasourceCatalog(
            @Nullable List<PostgresDatasourceProperties> postgres,
            @Nullable List<MongoDatasourceProperties> mongo,
            @Nullable List<BigQueryDatasourceProperties> bigquery) {
        Map<String, DatasourceType> map = new LinkedHashMap<>();
        addAll(map, postgres, DatasourceType.postgres);
        addAll(map, mongo, DatasourceType.mongo);
        addAll(map, bigquery, DatasourceType.bigquery);
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
        throw new IllegalArgumentException("Unsupported datasource property: " + property);
    }
}
