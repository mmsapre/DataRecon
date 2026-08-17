package com.mms.data.recon.config;

import com.mms.data.recon.dataset.DatasourceType;
import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceCatalogTest {

    @Test
    void mapsNamedDatasourcesToTypes() {
        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("landing"), new PostgresDatasourceProperties("master")),
                List.of(new MongoDatasourceProperties("mongo")),
                List.of(new BigQueryDatasourceProperties("bq"))
        );

        assertEquals(DatasourceType.postgres, catalog.require("landing"));
        assertEquals(DatasourceType.postgres, catalog.require("master"));
        assertEquals(DatasourceType.mongo, catalog.require("mongo"));
        assertEquals(DatasourceType.bigquery, catalog.require("bq"));
        assertTrue(catalog.names().containsAll(List.of("landing", "master", "mongo", "bq")));
    }

    @Test
    void rejectsNameClashAcrossStores() {
        assertThrows(ConfigurationException.class, () -> new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("shared")),
                List.of(new MongoDatasourceProperties("shared")),
                List.of()
        ));
    }

    @Test
    void unknownNameFails() {
        DatasourceCatalog catalog = new DatasourceCatalog(List.of(), List.of(), List.of());
        assertThrows(ConfigurationException.class, () -> catalog.require("missing"));
    }
}
