package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.CalciteBigQueryCatalog;
import com.mms.data.recon.config.CalciteFileCatalog;
import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoClientCatalog;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresConnectionFactoryCatalog;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.config.Tags;
import com.mms.data.recon.dataset.DatasourceType;
import com.mms.data.recon.dataset.DomainConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Setup-first catalog for named source/target datasources. Domains and profiles
 * attach these names after they exist here.
 */
@Component
public class DatasourceRegistry {

    private final DatasourceCatalog catalog;
    private final PostgresConnectionFactoryCatalog postgres;
    private final MongoClientCatalog mongo;
    private final CalciteBigQueryCatalog bigQuery;
    private final CalciteFileCatalog files;

    public DatasourceRegistry(
            DatasourceCatalog catalog,
            PostgresConnectionFactoryCatalog postgres,
            MongoClientCatalog mongo,
            CalciteBigQueryCatalog bigQuery,
            CalciteFileCatalog files) {
        this.catalog = catalog;
        this.postgres = postgres;
        this.mongo = mongo;
        this.bigQuery = bigQuery;
        this.files = files;
    }

    public List<DatasourceApiModel> list(String tag) {
        return catalog.asMap().entrySet().stream()
                .filter(entry -> Tags.matches(catalog.tagsOf(entry.getKey()), tag))
                .map(entry -> new DatasourceApiModel(
                        entry.getKey(),
                        entry.getValue().name(),
                        catalog.tagsOf(entry.getKey())
                ))
                .sorted(Comparator.comparing(DatasourceApiModel::name))
                .toList();
    }

    public DatasourceApiModel get(String name) {
        DatasourceType type = requireKnown(name);
        return new DatasourceApiModel(name, type.name(), catalog.tagsOf(name));
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
        DatasourceType type = DatasourceType.valueOf(existing.type());
        switch (type) {
            case postgres -> postgres.unregister(name);
            case mongo -> mongo.unregister(name);
            case bigquery -> bigQuery.unregister(name);
            case file -> files.unregister(name);
        }
        catalog.unregister(name);
        return existing;
    }

    private DatasourceApiModel upsert(String name, DatasourceUpsertRequest request, boolean creating) {
        if (request == null) {
            throw badRequest("Datasource body is required");
        }
        DatasourceType type = request.getType();
        if (type == null) {
            if (creating) {
                throw badRequest("type is required (postgres|mongo|bigquery|file)");
            }
            type = catalog.require(name);
        }
        List<String> tags = Tags.normalize(request.getTags());
        try {
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
            catalog.register(name, type, tags);
            return get(name);
        } catch (ConfigurationException | IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
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
