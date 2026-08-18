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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DatasourceCatalog {

    private final ConcurrentHashMap<String, DatasourceType> types = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> tagsByName = new ConcurrentHashMap<>();

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
        addAll(postgres, DatasourceType.postgres);
        addAll(mongo, DatasourceType.mongo);
        addAll(bigquery, DatasourceType.bigquery);
        addAll(files, DatasourceType.file);
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
        return Set.copyOf(types.keySet());
    }

    public Map<String, DatasourceType> asMap() {
        return Map.copyOf(types);
    }

    public List<String> tagsOf(String datasourceRef) {
        return tagsByName.getOrDefault(datasourceRef, List.of());
    }

    public synchronized void register(String name, DatasourceType type, List<String> tags) {
        if (name == null || name.isBlank()) {
            throw new ConfigurationException("Datasource name is required");
        }
        if (type == null) {
            throw new ConfigurationException("Datasource type is required");
        }
        DatasourceType existing = types.put(name, type);
        if (existing != null && existing != type) {
            types.put(name, existing);
            throw new ConfigurationException(
                    "Datasource [" + name + "] is already registered as " + existing
            );
        }
        tagsByName.put(name, Tags.normalize(tags));
    }

    public synchronized void unregister(String name) {
        types.remove(name);
        tagsByName.remove(name);
    }

    private void addAll(List<?> properties, DatasourceType type) {
        if (properties == null) {
            return;
        }
        for (Object property : properties) {
            String name = nameOf(property);
            List<String> tags = tagsOfProperty(property);
            DatasourceType existing = types.put(name, type);
            if (existing != null && existing != type) {
                throw new ConfigurationException(
                        "Datasource [" + name + "] is configured as both " + existing + " and " + type
                );
            }
            tagsByName.put(name, Tags.normalize(tags));
        }
    }

    private static List<String> tagsOfProperty(Object property) {
        if (property instanceof PostgresDatasourceProperties postgres) {
            return postgres.getTags();
        }
        if (property instanceof MongoDatasourceProperties mongo) {
            return mongo.getTags();
        }
        if (property instanceof BigQueryDatasourceProperties bigquery) {
            return bigquery.getTags();
        }
        if (property instanceof FileDatasourceProperties file) {
            return file.getTags();
        }
        return List.of();
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
