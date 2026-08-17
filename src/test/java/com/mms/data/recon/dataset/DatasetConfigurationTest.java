package com.mms.data.recon.dataset;

import com.mms.data.recon.config.RecConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetConfigurationTest {

    @Test
    void producesDatasourceDescriptor() {
        DatasetConfiguration configuration = new DatasetConfiguration();
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("source");
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("target");
        configuration.setSource(source);
        configuration.setTarget(target);

        assertEquals("(source -> target)", configuration.getDatasourceDescriptor());
    }

    @Test
    void resolvesHashingStrategyFromDefaults() {
        DatasetConfiguration configuration = new DatasetConfiguration();
        configuration.setSource(new DataLoadDefinition());
        configuration.setTarget(new DataLoadDefinition());
        configuration.applyDefaults(new RecConfiguration.Defaults());

        assertEquals(HashingStrategy.TypeLenient, configuration.getHashingStrategy());

        DatasetConfiguration strict = new DatasetConfiguration();
        strict.setSource(new DataLoadDefinition());
        strict.setTarget(new DataLoadDefinition());
        strict.setHashingStrategy(HashingStrategy.TypeStrict);
        strict.applyDefaults(new RecConfiguration.Defaults());

        assertEquals(HashingStrategy.TypeStrict, strict.getHashingStrategy());
    }

    @Test
    void initializeRequiresSourceAndTarget() {
        DatasetConfiguration configuration = new DatasetConfiguration();
        configuration.setId("missing");
        assertThrows(IllegalArgumentException.class, configuration::initialize);
    }

    @Test
    void attachesDatasourcesAndSharedMigrationKeyToSourceAndTarget() {
        DatasetConfiguration profile = new DatasetConfiguration();
        profile.setId("party.pg-pg");
        ProfileDatasources datasources = new ProfileDatasources();
        datasources.setSource("landing");
        datasources.setTarget("master");
        profile.setDatasources(datasources);
        profile.setMigrationKey(MigrationKeySpec.composite(java.util.List.of("party_id", "country_code")));

        DataLoadDefinition source = new DataLoadDefinition();
        source.setSchema("landing");
        source.setTable("party");
        source.setFields(java.util.List.of("party_name"));
        DataLoadDefinition target = new DataLoadDefinition();
        target.setSchema("master");
        target.setTable("party");
        target.setFields(java.util.List.of("party_name"));
        profile.setSource(source);
        profile.setTarget(target);
        profile.initialize();

        assertEquals("landing", source.getDatasource());
        assertEquals("master", target.getDatasource());
        assertEquals(MigrationKeyType.COMPOSITE, source.getMigrationKey().getType());
        assertEquals(MigrationKeyType.COMPOSITE, target.getMigrationKey().getType());
        assertEquals(
                "SELECT CAST(party_id AS TEXT) || '|' || CAST(country_code AS TEXT) AS \"MigrationKey\", party_name FROM landing.party",
                source.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void sideDatasourceOverridesProfileAttachment() {
        DatasetConfiguration profile = new DatasetConfiguration();
        ProfileDatasources datasources = new ProfileDatasources();
        datasources.setSource("landing");
        datasources.setTarget("master");
        profile.setDatasources(datasources);
        profile.setMigrationKey("party_id");

        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasource("override");
        source.setTable("party");
        source.setFields(java.util.List.of("party_name"));
        DataLoadDefinition target = new DataLoadDefinition();
        target.setTable("party");
        target.setFields(java.util.List.of("party_name"));
        profile.setSource(source);
        profile.setTarget(target);
        profile.initialize();

        assertEquals("override", source.getDatasource());
        assertEquals("master", target.getDatasource());
        assertEquals(MigrationKeyType.SINGLE, profile.getMigrationKey().getType());
    }
}
