package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DatasourceType;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.MigrationKeySpec;
import com.mms.data.recon.dataset.ProfileDatasources;
import com.mms.data.recon.dataset.ReconMode;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainControllerTest {

    @Test
    void listsConfiguredDomainsAndProfiles() {
        DatasetConfiguration profile = new DatasetConfiguration();
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("landing");
        source.setType(DatasourceType.postgres);
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("bq");
        target.setType(DatasourceType.bigquery);
        profile.setSource(source);
        profile.setTarget(target);
        profile.setHashingStrategy(HashingStrategy.TypeLenient);
        profile.setMigrationKey("party_id");

        DomainConfiguration domain = new DomainConfiguration();
        Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
        profiles.put("pg-bigquery", profile);
        domain.setProfiles(profiles);

        RecConfiguration configuration = new RecConfiguration();
        Map<String, DomainConfiguration> domains = new LinkedHashMap<>();
        domains.put("party", domain);
        configuration.setDomains(domains);

        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("landing")),
                List.of(),
                List.of(new BigQueryDatasourceProperties("bq")),
                List.of()
        );
        DomainController controller = new DomainController(
                configuration,
                catalog,
                new RecCatalogService(configuration, catalog)
        );

        List<DomainApiModel> models = controller.listDomains(null);
        assertEquals(1, models.size());
        assertEquals("party", models.get(0).id());
        assertEquals(1, models.get(0).profiles().size());
        assertEquals(List.of(), models.get(0).tags());

        ProfileApiModel listed = controller.getProfile("party", "pg-bigquery");
        assertEquals("party", listed.domainId());
        assertEquals("pg-bigquery", listed.profileId());
        assertEquals("party.pg-bigquery", listed.id());
        assertEquals("landing", listed.sourceDatasource());
        assertEquals("postgres", listed.sourceType());
        assertEquals("bq", listed.targetDatasource());
        assertEquals("bigquery", listed.targetType());
        assertEquals("TypeLenient", listed.hashingStrategy());
        assertEquals("SINGLE", listed.migrationKeyType());
        assertEquals(List.of("party_id"), listed.migrationKeyColumns());
        assertEquals("MISMATCH_DETAILS", listed.reconMode());
        assertEquals(List.of(), listed.conditionFields());
        assertEquals(List.of(), listed.tags());
    }

    @Test
    void createsDomainAndProfileThroughWriteApis() {
        RecConfiguration configuration = new RecConfiguration();
        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("landing")),
                List.of(),
                List.of(new BigQueryDatasourceProperties("bq")),
                List.of()
        );
        DomainController controller = new DomainController(
                configuration,
                catalog,
                new RecCatalogService(configuration, catalog)
        );

        DomainUpsertRequest domain = new DomainUpsertRequest();
        domain.setId("account");
        domain.setTags(List.of("finance"));
        DomainApiModel createdDomain = controller.createDomain(domain).getBody();
        assertEquals("account", createdDomain.id());
        assertEquals("data-recon", createdDomain.createdBy());
        assertEquals(1, createdDomain.version());
        assertEquals(true, createdDomain.active());
        assertEquals(List.of("finance"), controller.getDomain("account").tags());

        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId("pg-bq");
        profile.setTags(List.of("nightly"));
        profile.setMigrationKey(MigrationKeySpec.single("account_id"));
        SideRequest source = new SideRequest();
        source.setDatasource("landing");
        source.setTable("account");
        source.setFields(List.of("name"));
        profile.setSource(source);
        SideRequest target = new SideRequest();
        target.setDatasource("bq");
        target.setTable("account");
        target.setFields(List.of("name"));
        profile.setTarget(target);

        ProfileApiModel created = controller.createProfile("account", profile).getBody();
        assertEquals("landing", created.sourceDatasource());
        assertEquals("postgres", created.sourceType());
        assertEquals("bq", created.targetDatasource());
        assertEquals("bigquery", created.targetType());
        assertEquals(List.of("nightly"), created.tags());
        assertEquals("data-recon", created.createdBy());
        assertEquals(1, created.version());
        assertEquals(1, controller.listProfiles("account", "nightly").size());
        ProfileApiModel updated = controller.updateProfile("account", "pg-bq", reconPolicy());
        assertEquals("FIELD_DETAILS", updated.reconMode());
        assertEquals(2, updated.version());
        assertEquals(true, updated.active());
        assertEquals("data-recon", updated.updatedBy());

        AttachDatasourcesRequest attach = new AttachDatasourcesRequest();
        attach.setSource("landing");
        attach.setTarget("bq");
        assertEquals("landing", controller.attachDatasources("account", "pg-bq", attach).sourceDatasource());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteProfile("account", "pg-bq").getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteDomain("account").getStatusCode());
    }

    @Test
    void domainDatasourcesAreInheritedByProfiles() {
        PostgresDatasourceProperties landing = new PostgresDatasourceProperties("landing");
        landing.setSchema("landing");
        PostgresDatasourceProperties master = new PostgresDatasourceProperties("master");
        master.setSchema("master");

        RecConfiguration configuration = new RecConfiguration();
        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(landing, master),
                List.of(),
                List.of(),
                List.of()
        );
        DomainController controller = new DomainController(
                configuration,
                catalog,
                new RecCatalogService(configuration, catalog)
        );

        DomainUpsertRequest domain = new DomainUpsertRequest();
        domain.setId("party");
        ProfileDatasources domainDs = new ProfileDatasources();
        domainDs.setSource("landing");
        domainDs.setTarget("master");
        domain.setDatasources(domainDs);
        DomainApiModel createdDomain = controller.createDomain(domain).getBody();
        assertEquals("landing", createdDomain.sourceDatasource());
        assertEquals("master", createdDomain.targetDatasource());

        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId("pg-pg-inherited");
        profile.setMigrationKey(MigrationKeySpec.single("party_id"));
        SideRequest src = new SideRequest();
        src.setTable("party");
        src.setFields(List.of("party_name"));
        profile.setSource(src);
        SideRequest tgt = new SideRequest();
        tgt.setTable("party");
        tgt.setFields(List.of("party_name"));
        profile.setTarget(tgt);

        ProfileApiModel created = controller.createProfile("party", profile).getBody();
        assertEquals("landing", created.sourceDatasource());
        assertEquals("master", created.targetDatasource());
        assertEquals("postgres", created.sourceType());
        assertEquals("postgres", created.targetType());
    }

    @Test
    void sharedDatasourceSchemaIsInheritedByProfiles() {
        PostgresDatasourceProperties landing = new PostgresDatasourceProperties("landing");
        landing.setSchema("landing");
        PostgresDatasourceProperties master = new PostgresDatasourceProperties("master");
        master.setSchema("master");

        RecConfiguration configuration = new RecConfiguration();
        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(landing, master),
                List.of(),
                List.of(),
                List.of()
        );
        DomainController controller = new DomainController(
                configuration,
                catalog,
                new RecCatalogService(configuration, catalog)
        );

        DomainUpsertRequest domain = new DomainUpsertRequest();
        domain.setId("party");
        controller.createDomain(domain);

        ProfileUpsertRequest first = profileAttaching("pg-pg-a", "landing", "master");
        ProfileUpsertRequest second = profileAttaching("pg-pg-b", "landing", "master");
        controller.createProfile("party", first);
        controller.createProfile("party", second);

        assertEquals("landing", configuration.requireProfile("party", "pg-pg-a").getSource().getSchema());
        assertEquals("master", configuration.requireProfile("party", "pg-pg-a").getTarget().getSchema());
        assertEquals("landing", configuration.requireProfile("party", "pg-pg-b").getSource().getSchema());
        assertEquals("master", configuration.requireProfile("party", "pg-pg-b").getTarget().getSchema());
    }

    private static ProfileUpsertRequest profileAttaching(String id, String source, String target) {
        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId(id);
        profile.setMigrationKey(MigrationKeySpec.single("party_id"));
        SideRequest src = new SideRequest();
        src.setDatasource(source);
        src.setTable("party");
        src.setFields(List.of("party_name"));
        profile.setSource(src);
        SideRequest tgt = new SideRequest();
        tgt.setDatasource(target);
        tgt.setTable("party");
        tgt.setFields(List.of("party_name"));
        profile.setTarget(tgt);
        return profile;
    }

    private static ProfileUpsertRequest reconPolicy() {
        ProfileUpsertRequest request = new ProfileUpsertRequest();
        ReconRunRequest recon = new ReconRunRequest();
        recon.setMode(ReconMode.FIELD_DETAILS);
        recon.setConditionFields(List.of("name"));
        request.setRecon(recon);
        return request;
    }
}
