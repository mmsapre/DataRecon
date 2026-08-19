package com.mms.data.recon.config;

import com.mms.data.recon.dataset.DatasourceType;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of <strong>business</strong> datasources.
 * Populated from the recon catalog table ({@code rec_datasource}) at start via
 * {@code CatalogBootstrap}, and updated via API — not from application YAML.
 * Indexed by name and by schema. Profiles attach by either key; execute-time
 * loaders look up the canonical name in type-specific registries.
 *
 * <p>Contrast: the primary recon JDBC pool is built from properties only
 * ({@link ReconJdbcFactory} / {@code mms.recon.database}).
 */
@Component
public class DatasourceCatalog {

    public record Entry(String name, DatasourceType type, String schema, List<String> tags) {}

    private final ConcurrentHashMap<String, Entry> byName = new ConcurrentHashMap<>();
    /** schema → datasource name (for attach/lookup by schema). */
    private final ConcurrentHashMap<String, String> nameBySchema = new ConcurrentHashMap<>();

    /** Empty at construction; CatalogBootstrap loads rows from tables. */
    public DatasourceCatalog() {}

    /** Test helper: seed from in-memory property lists (not used at runtime). */
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

    /** Resolve a profile attach ref: datasource name or schema → canonical name. */
    public Optional<String> resolveName(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String key = ref.trim();
        if (byName.containsKey(key)) {
            return Optional.of(key);
        }
        return Optional.ofNullable(nameBySchema.get(key));
    }

    public Optional<Entry> entryOf(String ref) {
        return resolveName(ref).map(byName::get);
    }

    public Optional<DatasourceType> typeOf(String datasourceRef) {
        return entryOf(datasourceRef).map(Entry::type);
    }

    public boolean has(String datasourceRef) {
        return resolveName(datasourceRef).isPresent();
    }

    public DatasourceType require(String datasourceRef) {
        return entryOf(datasourceRef)
                .map(Entry::type)
                .orElseThrow(() -> new ConfigurationException(
                        "Unknown datasourceRef [" + datasourceRef + "]. Known names: " + names()
                                + "; schemas: " + nameBySchema.keySet()
                ));
    }

    /** Canonical name for attach/execute; throws if unknown. */
    public String requireName(String ref) {
        return resolveName(ref).orElseThrow(() -> new ConfigurationException(
                "Unknown datasourceRef [" + ref + "]. Known names: " + names()
                        + "; schemas: " + nameBySchema.keySet()
        ));
    }

    public Set<String> names() {
        return Set.copyOf(byName.keySet());
    }

    public Map<String, DatasourceType> asMap() {
        Map<String, DatasourceType> out = new java.util.LinkedHashMap<>();
        byName.forEach((name, entry) -> out.put(name, entry.type()));
        return Map.copyOf(out);
    }

    /** schema → name index (read-only snapshot). */
    public Map<String, String> schemaIndex() {
        return Map.copyOf(nameBySchema);
    }

    public List<String> tagsOf(String datasourceRef) {
        return entryOf(datasourceRef).map(Entry::tags).orElse(List.of());
    }

    public Optional<String> schemaOf(String datasourceRef) {
        return entryOf(datasourceRef).map(Entry::schema).filter(s -> s != null && !s.isBlank());
    }

    public synchronized void register(String name, DatasourceType type, List<String> tags) {
        register(name, type, tags, null);
    }

    public synchronized void register(String name, DatasourceType type, List<String> tags, String schema) {
        if (name == null || name.isBlank()) {
            throw new ConfigurationException("Datasource name is required");
        }
        if (type == null) {
            throw new ConfigurationException("Datasource type is required");
        }
        String trimmedName = name.trim();
        Entry existing = byName.get(trimmedName);
        if (existing != null && existing.type() != type) {
            throw new ConfigurationException(
                    "Datasource [" + trimmedName + "] is already registered as " + existing.type()
            );
        }
        String trimmedSchema = schema == null || schema.isBlank() ? null : schema.trim();
        if (trimmedSchema != null) {
            String owner = nameBySchema.get(trimmedSchema);
            if (owner != null && !owner.equals(trimmedName)) {
                throw new ConfigurationException(
                        "Schema [" + trimmedSchema + "] is already bound to datasource [" + owner + "]"
                );
            }
        }
        Entry previous = byName.put(trimmedName, new Entry(trimmedName, type, trimmedSchema, Tags.normalize(tags)));
        if (previous != null && previous.schema() != null && !previous.schema().equals(trimmedSchema)) {
            nameBySchema.remove(previous.schema(), trimmedName);
        }
        if (trimmedSchema != null) {
            nameBySchema.put(trimmedSchema, trimmedName);
        }
    }

    public synchronized void unregister(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Entry removed = byName.remove(name.trim());
        if (removed != null && removed.schema() != null) {
            nameBySchema.remove(removed.schema(), removed.name());
        }
    }

    private void addAll(List<?> properties, DatasourceType type) {
        if (properties == null) {
            return;
        }
        for (Object property : properties) {
            register(nameOf(property), type, tagsOfProperty(property), schemaOfProperty(property));
        }
    }

    private static String schemaOfProperty(Object property) {
        if (property instanceof PostgresDatasourceProperties postgres) {
            return postgres.resolveSchema();
        }
        if (property instanceof BigQueryDatasourceProperties bigquery) {
            return bigquery.resolveDefaultSchema();
        }
        return null;
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
