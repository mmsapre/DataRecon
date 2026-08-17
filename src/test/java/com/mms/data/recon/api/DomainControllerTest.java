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
import com.mms.data.recon.dataset.ReconMode;
import io.micronaut.http.HttpStatus;
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
        profile.setSchedule("60s");
        profile.setMigrationKey("party_id");

        DomainConfiguration domain = new DomainConfiguration();
        domain.setSchedule("1h");
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
                new RecCatalogService(configuration, catalog, null)
        );

        List<DomainApiModel> models = controller.listDomains();
        assertEquals(1, models.size());
        assertEquals("party", models.get(0).id());
        assertEquals("1h", models.get(0).schedule());
        assertEquals(1, models.get(0).profiles().size());

        ProfileApiModel listed = controller.getProfile("party", "pg-bigquery");
        assertEquals("party", listed.domainId());
        assertEquals("pg-bigquery", listed.profileId());
        assertEquals("party.pg-bigquery", listed.id());
        assertEquals("landing", listed.sourceDatasource());
        assertEquals("postgres", listed.sourceType());
        assertEquals("bq", listed.targetDatasource());
        assertEquals("bigquery", listed.targetType());
        assertEquals("TypeLenient", listed.hashingStrategy());
        assertEquals("60s", listed.schedule());
        assertEquals("SINGLE", listed.migrationKeyType());
        assertEquals(List.of("party_id"), listed.migrationKeyColumns());
        assertEquals("MISMATCH_DETAILS", listed.reconMode());
        assertEquals(List.of(), listed.conditionFields());
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
                new RecCatalogService(configuration, catalog, null)
        );

        DomainUpsertRequest domain = new DomainUpsertRequest();
        domain.setId("account");
        domain.setSchedule("30m");
        assertEquals("account", controller.createDomain(domain).getBody().orElseThrow().id());
        assertEquals("30m", controller.getDomain("account").schedule());

        ProfileUpsertRequest profile = new ProfileUpsertRequest();
        profile.setId("pg-bq");
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

        ProfileApiModel created = controller.createProfile("account", profile).getBody().orElseThrow();
        assertEquals("landing", created.sourceDatasource());
        assertEquals("postgres", created.sourceType());
        assertEquals("bq", created.targetDatasource());
        assertEquals("bigquery", created.targetType());
        assertEquals("FIELD_DETAILS", controller.updateProfile("account", "pg-bq", reconPolicy()).reconMode());

        AttachDatasourcesRequest attach = new AttachDatasourcesRequest();
        attach.setSource("landing");
        attach.setTarget("bq");
        assertEquals("landing", controller.attachDatasources("account", "pg-bq", attach).sourceDatasource());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteProfile("account", "pg-bq").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteDomain("account").getStatus());
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
