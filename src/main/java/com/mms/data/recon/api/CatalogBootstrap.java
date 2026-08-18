package com.mms.data.recon.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads active catalog rows from the recon DB into in-memory registries after Flyway.
 */
@Component
@Order(100)
public class CatalogBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogBootstrap.class);

    private final CatalogRepository catalogRepository;
    private final DatasourceRegistry datasources;
    private final RecCatalogService catalog;

    public CatalogBootstrap(
            CatalogRepository catalogRepository,
            DatasourceRegistry datasources,
            RecCatalogService catalog) {
        this.catalogRepository = catalogRepository;
        this.datasources = datasources;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        int ds = 0;
        for (CatalogRepository.StoredDatasource row : catalogRepository.findActiveDatasources()) {
            DatasourceUpsertRequest request = row.config() == null ? new DatasourceUpsertRequest() : row.config();
            request.setName(row.name());
            request.setType(row.type());
            if (request.getTags() == null) {
                request.setTags(row.tags());
            }
            datasources.hydrate(request, row.audit());
            ds++;
        }
        int domains = 0;
        for (CatalogRepository.StoredDomain row : catalogRepository.findActiveDomains()) {
            DomainUpsertRequest request = row.config() == null ? new DomainUpsertRequest() : row.config();
            request.setId(row.domainId());
            if (request.getTags() == null) {
                request.setTags(row.tags());
            }
            catalog.hydrateDomain(row.domainId(), request, row.audit());
            domains++;
        }
        int profiles = 0;
        for (CatalogRepository.StoredProfile row : catalogRepository.findActiveProfiles()) {
            ProfileUpsertRequest request = row.config() == null ? new ProfileUpsertRequest() : row.config();
            request.setId(row.profileId());
            if (request.getTags() == null) {
                request.setTags(row.tags());
            }
            catalog.hydrateProfile(row.domainId(), row.profileId(), request, row.audit());
            profiles++;
        }
        log.info("Catalog bootstrap loaded {} datasources, {} domains, {} profiles", ds, domains, profiles);
    }
}
