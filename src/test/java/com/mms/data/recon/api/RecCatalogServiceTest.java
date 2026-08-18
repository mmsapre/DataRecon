package com.mms.data.recon.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasourceType;
import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.MigrationKeySpec;
import com.mms.data.recon.dataset.ProfileDatasources;
import com.mms.data.recon.dataset.ReconMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecCatalogServiceTest {

    @Test
    void createsDomainProfileSourceTargetAttachesDatasourceAndReconPolicy() {
        RecCatalogService catalog = catalog();

        DomainUpsertRequest domainRequest = new DomainUpsertRequest();
        domainRequest.setId("party");
        domainRequest.setSchedule("1h");
        domainRequest.setHashingStrategy(HashingStrategy.TypeStrict);
        ReconRunRequest domainRecon = new ReconRunRequest();
        domainRecon.setMode(ReconMode.COUNTS);
        domainRequest.setRecon(domainRecon);
        catalog.createDomain(domainRequest);

        ProfileUpsertRequest profileRequest = new ProfileUpsertRequest();
        profileRequest.setId("pg-csv");
        profileRequest.setMigrationKey(MigrationKeySpec.single("party_id"));
        ReconRunRequest profileRecon = new ReconRunRequest();
        profileRecon.setMode(ReconMode.FIELD_DETAILS);
        profileRecon.setConditionFields(List.of("party_name", "status"));
        profileRequest.setRecon(profileRecon);
        catalog.createProfile("party", profileRequest);

        SideRequest source = new SideRequest();
        source.setSchema("public");
        source.setTable("party_landing");
        source.setFields(List.of("party_name", "status"));
        catalog.updateSource("party", "pg-csv", source);

        SideRequest target = new SideRequest();
        target.setTable("party");
        target.setFields(List.of("party_name", "status"));
        catalog.updateTarget("party", "pg-csv", target);

        AttachDatasourcesRequest attach = new AttachDatasourcesRequest();
        attach.setSource("landing");
        attach.setTarget("csv");
        var stored = catalog.attachDatasources("party", "pg-csv", attach);

        assertEquals("landing", stored.getSource().getDatasourceRef());
        assertEquals("csv", stored.getTarget().getDatasourceRef());
        assertEquals("public", stored.getSource().getSchema());
        assertEquals("party_landing", stored.getSource().getTable());
        assertEquals("party", stored.getTarget().getTable());
        assertEquals(ReconMode.FIELD_DETAILS, stored.resolvedRecon().resolvedMode());
        assertEquals(List.of("party_name", "status"), stored.resolvedRecon().resolvedConditionFields());
        assertEquals("party.pg-csv", stored.getId());

        AttachDatasourcesRequest overwrite = new AttachDatasourcesRequest();
        overwrite.setTarget("bq");
        stored = catalog.attachDatasources("party", "pg-csv", overwrite);
        assertEquals("landing", stored.getSource().getDatasourceRef());
        assertEquals("bq", stored.getTarget().getDatasourceRef());
    }

    @Test
    void createDomainRejectsDuplicateAndUnknownDatasource() {
        RecCatalogService catalog = catalog();
        DomainUpsertRequest request = new DomainUpsertRequest();
        request.setId("party");
        catalog.createDomain(request);

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class, () -> catalog.createDomain(request));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId("pg-pg");
        SideRequest source = new SideRequest();
        source.setDatasource("missing");
        profile.setSource(source);
        ResponseStatusException unknown = assertThrows(
                ResponseStatusException.class,
                () -> catalog.createProfile("party", profile)
        );
        assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());
        assertTrue(unknown.getMessage().contains("Unknown datasourceRef"));
    }

    @Test
    void unknownDomainOrProfileIsNotFound() {
        RecCatalogService catalog = catalog();
        DomainUpsertRequest update = new DomainUpsertRequest();
        update.setSchedule("60s");
        ResponseStatusException missingDomain = assertThrows(
                ResponseStatusException.class,
                () -> catalog.updateDomain("party", update)
        );
        assertEquals(HttpStatus.NOT_FOUND, missingDomain.getStatusCode());

        DomainUpsertRequest create = new DomainUpsertRequest();
        create.setId("party");
        catalog.createDomain(create);
        ResponseStatusException missingProfile = assertThrows(
                ResponseStatusException.class,
                () -> catalog.updateSource("party", "pg-pg", new SideRequest())
        );
        assertEquals(HttpStatus.NOT_FOUND, missingProfile.getStatusCode());
    }

    @Test
    void createProfileWithDatasourcesAndSidesInOneCall() {
        RecCatalogService catalog = catalog();
        DomainUpsertRequest domain = new DomainUpsertRequest();
        domain.setId("party");
        catalog.createDomain(domain);

        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId("pg-mongo");
        ProfileDatasources datasources = new ProfileDatasources();
        datasources.setSource("landing");
        datasources.setTarget("mongo");
        profile.setDatasources(datasources);
        profile.setMigrationKey(MigrationKeySpec.single("party_id"));
        SideRequest source = new SideRequest();
        source.setSchema("public");
        source.setTable("party");
        source.setFields(List.of("party_name"));
        profile.setSource(source);
        SideRequest target = new SideRequest();
        target.setCollection("party");
        target.setFields(List.of("party_name"));
        profile.setTarget(target);

        var stored = catalog.createProfile("party", profile);
        assertEquals("landing", stored.getSource().getDatasourceRef());
        assertEquals("mongo", stored.getTarget().getDatasourceRef());
        assertEquals(DatasourceType.mongo, stored.getTarget().resolveType(
                new DatasourceCatalog(
                        List.of(new PostgresDatasourceProperties("landing")),
                        List.of(new MongoDatasourceProperties("mongo")),
                        List.of(new BigQueryDatasourceProperties("bq")),
                        List.of(new FileDatasourceProperties("csv"))
                )
        ));
        assertNull(stored.getSchedule());
    }

    private static RecCatalogService catalog() {
        DatasourceCatalog datasources = new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("landing")),
                List.of(new MongoDatasourceProperties("mongo")),
                List.of(new BigQueryDatasourceProperties("bq")),
                List.of(new FileDatasourceProperties("csv"))
        );
        return new RecCatalogService(new RecConfiguration(), datasources, null);
    }
}
