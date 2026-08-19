package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.CalciteBigQueryCatalog;
import com.mms.data.recon.config.CalciteFileCatalog;
import com.mms.data.recon.config.CatalogAudit;
import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoClientCatalog;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresConnectionFactoryCatalog;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.config.Tags;
import com.mms.data.recon.dataset.DatasourceType;
import com.mms.data.recon.dataset.DomainConfiguration;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for named business source/target datasources (Postgres, Mongo, BigQuery, file).
 * Configured via API; persisted in the primary recon DB catalog. Runtime clients/pools are
 * looked up from type-specific registries only when a profile executes.
 * Domains and profiles attach these names after they exist here.
 */
@Component
public class DatasourceRegistry {

    private final DatasourceCatalog catalog;
    private final PostgresConnectionFactoryCatalog postgres;
    private final MongoClientCatalog mongo;
    private final CalciteBigQueryCatalog bigQuery;
    private final CalciteFileCatalog files;
    private final ConcurrentHashMap<String, CatalogAudit> audits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DatasourceUpsertRequest> configs = new ConcurrentHashMap<>();
    @Nullable
    private final CatalogRepository catalogRepository;
    private final String actor;

    public DatasourceRegistry(
            DatasourceCatalog catalog,
            PostgresConnectionFactoryCatalog postgres,
            MongoClientCatalog mongo,
            CalciteBigQueryCatalog bigQuery,
            CalciteFileCatalog files) {
        this(catalog, postgres, mongo, bigQuery, files, null, null);
    }

    @Autowired
    public DatasourceRegistry(
            DatasourceCatalog catalog,
            PostgresConnectionFactoryCatalog postgres,
            MongoClientCatalog mongo,
            CalciteBigQueryCatalog bigQuery,
            CalciteFileCatalog files,
            @Nullable CatalogRepository catalogRepository,
            @Nullable RecConfiguration configuration) {
        this.catalog = catalog;
        this.postgres = postgres;
        this.mongo = mongo;
        this.bigQuery = bigQuery;
        this.files = files;
        this.catalogRepository = catalogRepository;
        this.actor = configuration == null ? "data-recon" : configuration.getActor();
        for (String name : catalog.names()) {
            audits.putIfAbsent(name, CatalogAudit.create(actor));
        }
    }

    public List<DatasourceApiModel> list(String tag) {
        return catalog.asMap().entrySet().stream()
                .filter(entry -> Tags.matches(catalog.tagsOf(entry.getKey()), tag))
                .map(entry -> toModel(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DatasourceApiModel::name))
                .toList();
    }

    public DatasourceApiModel get(String name) {
        DatasourceType type = requireKnown(name);
        return toModel(name, type);
    }

    public synchronized DatasourceApiModel create(DatasourceUpsertRequest request) {
        String name = requireName(request == null ? null : request.getName());
        if (catalog.has(name)) {
            throw conflict("Datasource already exists: " + name);
        }
        return upsert(name, request, true);
    }

    public synchronized DatasourceApiModel update(String name, DatasourceUpsertRequest request) {
        requireKnown(name);
        return upsert(name, request, false);
    }

    public synchronized DatasourceApiModel delete(String name) {
        DatasourceApiModel existing = get(name);
        CatalogAudit previous = audits.getOrDefault(name, CatalogAudit.create(actor));
        CatalogAudit deactivated = previous.deactivate(actor);
        DatasourceType type = DatasourceType.valueOf(existing.type());
        switch (type) {
            case postgres -> postgres.unregister(name);
            case mongo -> mongo.unregister(name);
            case bigquery -> bigQuery.unregister(name);
            case file -> files.unregister(name);
        }
        catalog.unregister(name);
        audits.remove(name);
        configs.remove(name);
        if (catalogRepository != null) {
            catalogRepository.deactivateDatasource(name, deactivated);
        }
        return new DatasourceApiModel(
                existing.name(),
                existing.type(),
                existing.schema(),
                existing.tags(),
                deactivated.createdAt(),
                deactivated.createdBy(),
                deactivated.updatedAt(),
                deactivated.updatedBy(),
                false,
                deactivated.version()
        );
    }

    /** Load an already-persisted active datasource into runtime (no new DB write). */
    public synchronized void hydrate(DatasourceUpsertRequest request, CatalogAudit audit) {
        if (request == null || request.getName() == null) {
            return;
        }
        String name = requireName(request.getName());
        applyRuntime(name, request, true);
        audits.put(name, audit == null ? CatalogAudit.create(actor) : audit);
        configs.put(name, request);
    }

    private DatasourceApiModel upsert(String name, DatasourceUpsertRequest request, boolean creating) {
        if (request == null) {
            throw badRequest("Datasource body is required");
        }
        request.setName(name);
        if (!creating) {
            DatasourceUpsertRequest previousConfig = configs.get(name);
            if (previousConfig != null) {
                mergeDatasource(previousConfig, request);
            }
        }
        DatasourceType type = request.getType();
        if (type == null) {
            if (creating) {
                throw badRequest("type is required (postgres|mongo|bigquery|file)");
            }
            type = catalog.require(name);
            request.setType(type);
        }
        List<String> tags;
        if (request.getTags() != null) {
            tags = Tags.normalize(request.getTags());
        } else if (creating) {
            tags = List.of();
        } else {
            tags = catalog.tagsOf(name);
        }
        request.setTags(tags);

        CatalogAudit audit;
        if (creating) {
            audit = CatalogAudit.create(actor);
        } else {
            CatalogAudit previous = audits.getOrDefault(name, CatalogAudit.create(actor));
            CatalogAudit deactivated = previous.deactivate(actor);
            if (catalogRepository != null) {
                catalogRepository.deactivateDatasource(name, deactivated);
            }
            audit = previous.nextVersion(actor);
        }

        try {
            applyRuntime(name, request, creating);
            audits.put(name, audit);
            configs.put(name, request);
            if (catalogRepository != null) {
                catalogRepository.insertDatasource(name, type, tags, request, audit);
            }
            return toModel(name, type);
        } catch (ConfigurationException | IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
    }

    private void applyRuntime(String name, DatasourceUpsertRequest request, boolean creating) {
        DatasourceType type = request.getType();
        List<String> tags = Tags.normalize(request.getTags());
        switch (type) {
            case postgres -> {
                PostgresDatasourceProperties props = request.toPostgres(name);
                props.setTags(tags);
                postgres.register(props);
            }
            case mongo -> {
                MongoDatasourceProperties props = request.toMongo(name);
                props.setTags(tags);
                mongo.register(props);
            }
            case bigquery -> {
                BigQueryDatasourceProperties props = request.toBigQuery(name);
                props.setTags(tags);
                bigQuery.register(props);
            }
            case file -> {
                FileDatasourceProperties props = request.toFile(name);
                props.setTags(tags);
                files.register(props);
            }
        }
        if (!creating && catalog.has(name)) {
            // type may stay the same; re-register tags/schema
            catalog.unregister(name);
        }
        catalog.register(name, type, tags, defaultSchema(request, type));
    }

    private static String defaultSchema(DatasourceUpsertRequest request, DatasourceType type) {
        if (request.getSchema() != null && !request.getSchema().isBlank()) {
            return request.getSchema().trim();
        }
        if (type == DatasourceType.postgres) {
            String url = request.getUrl() != null && !request.getUrl().isBlank()
                    ? request.getUrl()
                    : request.getUri();
            String fromUri = PostgresConnectionFactoryCatalog.schemaFromUrl(url);
            if (fromUri != null) {
                return fromUri;
            }
        }
        if (type == DatasourceType.bigquery
                && request.getDataset() != null
                && !request.getDataset().isBlank()) {
            return request.getDataset().trim();
        }
        return null;
    }

    private static void mergeDatasource(DatasourceUpsertRequest base, DatasourceUpsertRequest patch) {
        if (patch.getType() == null) {
            patch.setType(base.getType());
        }
        if (patch.getTags() == null) {
            patch.setTags(base.getTags());
        }
        if (patch.getSchema() == null) {
            patch.setSchema(base.getSchema());
        }
        if (patch.getUrl() == null) {
            patch.setUrl(base.getUrl());
        }
        if (patch.getUri() == null) {
            patch.setUri(base.getUri());
        }
        if (patch.getHost() == null) {
            patch.setHost(base.getHost());
        }
        if (patch.getPort() == null) {
            patch.setPort(base.getPort());
        }
        if (patch.getDatabase() == null) {
            patch.setDatabase(base.getDatabase());
        }
        if (patch.getUsername() == null) {
            patch.setUsername(base.getUsername());
        }
        if (patch.getPassword() == null) {
            patch.setPassword(base.getPassword());
        }
        if (patch.getMaxSize() == null) {
            patch.setMaxSize(base.getMaxSize());
        }
        if (patch.getUri() == null) {
            patch.setUri(base.getUri());
        }
        if (patch.getAuthDatabase() == null) {
            patch.setAuthDatabase(base.getAuthDatabase());
        }
        if (patch.getJdbcUrl() == null) {
            patch.setJdbcUrl(base.getJdbcUrl());
        }
        if (patch.getDriverClassName() == null) {
            patch.setDriverClassName(base.getDriverClassName());
        }
        if (patch.getProjectId() == null) {
            patch.setProjectId(base.getProjectId());
        }
        if (patch.getDataset() == null) {
            patch.setDataset(base.getDataset());
        }
        if (patch.getCatalog() == null) {
            patch.setCatalog(base.getCatalog());
        }
        if (patch.getCredentialsFile() == null) {
            patch.setCredentialsFile(base.getCredentialsFile());
        }
        if (patch.getOauthType() == null) {
            patch.setOauthType(base.getOauthType());
        }
        if (patch.getCalciteSchema() == null) {
            patch.setCalciteSchema(base.getCalciteSchema());
        }
        if (patch.getPath() == null) {
            patch.setPath(base.getPath());
        }
        if (patch.getPattern() == null) {
            patch.setPattern(base.getPattern());
        }
        if (patch.getFormat() == null) {
            patch.setFormat(base.getFormat());
        }
        if (patch.getTable() == null) {
            patch.setTable(base.getTable());
        }
        if (patch.getSheet() == null) {
            patch.setSheet(base.getSheet());
        }
        if (patch.getDelimiter() == null) {
            patch.setDelimiter(base.getDelimiter());
        }
        if (patch.getHeader() == null) {
            patch.setHeader(base.getHeader());
        }
    }

    private DatasourceApiModel toModel(String name, DatasourceType type) {
        CatalogAudit audit = audits.getOrDefault(name, CatalogAudit.create(actor));
        return new DatasourceApiModel(
                name,
                type.name(),
                catalog.schemaOf(name).orElse(null),
                catalog.tagsOf(name),
                audit.createdAt(),
                audit.createdBy(),
                audit.updatedAt(),
                audit.updatedBy(),
                audit.active(),
                audit.version()
        );
    }

    private DatasourceType requireKnown(String name) {
        try {
            return catalog.require(name);
        } catch (ConfigurationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static String requireName(String name) {
        try {
            return DomainConfiguration.requireName("datasource", name);
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
