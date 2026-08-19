package com.mms.data.recon.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads active catalog rows from the primary recon DB into memory after Flyway.
 * Datasources are registered by <strong>name</strong> and indexed by <strong>schema</strong>
 * so profiles can attach via either key; connections are resolved from the registry at execute time.
 * Only {@code mms.recon.database} is required at start.
 */
@Component
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
            try {
                DatasourceUpsertRequest request = row.config() == null ? new DatasourceUpsertRequest() : row.config();
                request.setName(row.name());
                request.setType(row.type());
                if (request.getTags() == null) {
                    request.setTags(row.tags());
                }
                datasources.hydrate(request, row.audit());
                ds++;
                log.debug(
                        "Registry datasource name={} schema={} type={}",
                        row.name(),
                        request.getSchema(),
                        row.type());
            } catch (RuntimeException e) {
                log.warn("Skip datasource '{}': {}", row.name(), e.getMessage());
            }
        }
        int domains = 0;
        for (CatalogRepository.StoredDomain row : catalogRepository.findActiveDomains()) {
            try {
                DomainUpsertRequest request = row.config() == null ? new DomainUpsertRequest() : row.config();
                request.setId(row.domainId());
                if (request.getTags() == null) {
                    request.setTags(row.tags());
                }
                catalog.hydrateDomain(row.domainId(), request, row.audit());
                domains++;
            } catch (RuntimeException e) {
                log.warn("Skip domain '{}': {}", row.domainId(), e.getMessage());
            }
        }
        int profiles = 0;
        for (CatalogRepository.StoredProfile row : catalogRepository.findActiveProfiles()) {
            try {
                ProfileUpsertRequest request = row.config() == null ? new ProfileUpsertRequest() : row.config();
                request.setId(row.profileId());
                if (request.getTags() == null) {
                    request.setTags(row.tags());
                }
                catalog.hydrateProfile(row.domainId(), row.profileId(), request, row.audit());
                profiles++;
            } catch (RuntimeException e) {
                log.warn("Skip profile '{}.{}': {}", row.domainId(), row.profileId(), e.getMessage());
            }
        }
        log.info("Catalog bootstrap: {} datasources, {} domains, {} profiles", ds, domains, profiles);
    }
}
