package com.mms.data.recon.dataset;

import com.mms.data.recon.config.DatasourceCatalog;
import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataLoadDefinitionTest {

    private static final DatasourceCatalog CATALOG = new DatasourceCatalog(
            java.util.List.of(new com.mms.data.recon.config.PostgresDatasourceProperties("landing")),
            java.util.List.of(new com.mms.data.recon.config.MongoDatasourceProperties("mongo")),
            java.util.List.of(new com.mms.data.recon.config.BigQueryDatasourceProperties("bq")),
            java.util.List.of(new com.mms.data.recon.config.FileDatasourceProperties("csv"))
    );

    @Test
    void attachDatasourceOverwritesExistingRef() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasource("landing");
        definition.attachDatasource("csv");
        assertEquals("csv", definition.getDatasource());
        assertEquals("csv", definition.getDatasourceRef());
    }

    @Test
    void infersMongoFromCatalogOrCollection() {
        DataLoadDefinition fromCatalog = new DataLoadDefinition();
        fromCatalog.setDatasourceRef("mongo");
        assertEquals(DatasourceType.mongo, fromCatalog.resolveType(CATALOG));

        DataLoadDefinition fromCollection = new DataLoadDefinition();
        fromCollection.setDatasourceRef("custom");
        fromCollection.setCollection("party");
        assertEquals(DatasourceType.mongo, fromCollection.resolveType(ref -> Optional.empty()));
    }

    @Test
    void infersPostgresFromCatalog() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef("landing");
        assertEquals(DatasourceType.postgres, definition.resolveType(CATALOG));
    }

    @Test
    void infersFileFromCatalog() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef("csv");
        assertEquals(DatasourceType.file, definition.resolveType(CATALOG));
    }

    @Test
    void infersBigQueryFromCatalog() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef("bq");
        assertEquals(DatasourceType.bigquery, definition.resolveType(CATALOG));
    }

    @Test
    void generatesFileSelectWithQuotedMigrationKey() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setTable("party");
        definition.setMigrationKey("party_id");
        definition.setFields(List.of("party_name", "country_code", "status"));

        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name, country_code, status FROM party",
                definition.resolveQueryStatement(DatasourceType.file)
        );
    }

    @Test
    void explicitTypeOverridesCatalog() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef("landing");
        definition.setType(DatasourceType.bigquery);
        assertEquals(DatasourceType.bigquery, definition.resolveType(CATALOG));
    }

    @Test
    void unknownDatasourceRefFails() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef("missing");
        assertThrows(ConfigurationException.class, () -> definition.resolveType(CATALOG));
    }

    @Test
    void mongoQueryDefaultsToEmptyFilter() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.initialize("party-pg-mongo", DataLoadDefinition.Role.TARGET, java.nio.file.Path.of("queries"));

        assertEquals("{}", definition.resolveQueryStatement(DatasourceType.mongo));
    }

    @Test
    void generatesPostgresSelectForDifferentSchema() {
        DataLoadDefinition target = new DataLoadDefinition();
        target.initialize("party-pg-pg", DataLoadDefinition.Role.TARGET, java.nio.file.Path.of("queries"));
        target.setSchema("master");
        target.setTable("party");
        target.setMigrationKey("party_id");
        target.setFields(java.util.List.of("party_name", "country_code", "status"));

        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name, country_code, status FROM master.party",
                target.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void generatesPostgresSelectForSameSchemaDifferentTable() {
        DataLoadDefinition source = new DataLoadDefinition();
        source.initialize("party-pg-pg-same-schema", DataLoadDefinition.Role.SOURCE, java.nio.file.Path.of("queries"));
        source.setSchema("public");
        source.setTable("party_landing");
        source.setMigrationKey("party_id");
        source.setFields(java.util.List.of("party_name"));

        DataLoadDefinition target = new DataLoadDefinition();
        target.initialize("party-pg-pg-same-schema", DataLoadDefinition.Role.TARGET, java.nio.file.Path.of("queries"));
        target.setSchema("public");
        target.setTable("party_master");
        target.setMigrationKey("party_id");
        target.setFields(java.util.List.of("party_name"));

        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name FROM public.party_landing",
                source.resolveQueryStatement(DatasourceType.postgres)
        );
        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name FROM public.party_master",
                target.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void omittedSchemaUsesUnqualifiedTable() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setTable("party");
        definition.setMigrationKey("party_id");
        definition.setFields(java.util.List.of("party_name"));

        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name FROM party",
                definition.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void generatesPostgresSelectForCompositeKey() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setTable("party");
        definition.setMigrationKey(MigrationKeySpec.composite(List.of("party_id", "country_code")));
        definition.setFields(List.of("party_name"));

        assertEquals(
                "SELECT CAST(party_id AS TEXT) || '|' || CAST(country_code AS TEXT) AS \"MigrationKey\", party_name FROM party",
                definition.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void generatesSelectForDefinedKey() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setTable("party");
        definition.setMigrationKey(MigrationKeySpec.defined("concat(party_id, country_code)"));
        definition.setFields(List.of("party_name"));

        assertEquals(
                "SELECT concat(party_id, country_code) AS \"MigrationKey\", party_name FROM party",
                definition.resolveQueryStatement(DatasourceType.postgres)
        );
    }

    @Test
    void explicitQueryWinsOverTable() {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setSchema("master");
        definition.setTable("party");
        definition.setQuery("SELECT 1 AS \"MigrationKey\"");

        assertEquals("SELECT 1 AS \"MigrationKey\"", definition.resolveQueryStatement(DatasourceType.postgres));
    }

    @Test
    void inlineQueryIsUsedForPostgresMongoAndBigQuery() {
        DataLoadDefinition postgres = new DataLoadDefinition();
        postgres.setTable("party");
        postgres.setQuery("""
                SELECT party_id AS "MigrationKey", party_name, country_code, status
                FROM landing.party
                WHERE status = 'ACTIVE'
                """);
        assertEquals(
                "SELECT party_id AS \"MigrationKey\", party_name, country_code, status\n"
                        + "FROM landing.party\n"
                        + "WHERE status = 'ACTIVE'",
                postgres.resolveQueryStatement(DatasourceType.postgres)
        );

        DataLoadDefinition mongo = new DataLoadDefinition();
        mongo.setCollection("party");
        mongo.setQuery("{ \"status\": \"ACTIVE\" }");
        assertEquals("{ \"status\": \"ACTIVE\" }", mongo.resolveQueryStatement(DatasourceType.mongo));

        DataLoadDefinition bigquery = new DataLoadDefinition();
        bigquery.setTable("party");
        bigquery.setQuery("SELECT party_id AS MigrationKey, party_name FROM party WHERE status = 'ACTIVE'");
        assertEquals(
                "SELECT party_id AS MigrationKey, party_name FROM party WHERE status = 'ACTIVE'",
                bigquery.resolveQueryStatement(DatasourceType.bigquery)
        );
    }
}
