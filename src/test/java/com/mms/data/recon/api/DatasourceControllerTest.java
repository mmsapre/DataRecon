package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.CalciteBigQueryCatalog;
import com.mms.data.recon.config.CalciteFileCatalog;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoClientCatalog;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresConnectionFactoryCatalog;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import com.mms.data.recon.dataset.DatasourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceControllerTest {

    @Test
    void listsCreatesFiltersAndDeletesDatasources() {
        DatasourceController controller = controller(
                List.of(new PostgresDatasourceProperties("landing")),
                List.of(new MongoDatasourceProperties("mongo")),
                List.of(new BigQueryDatasourceProperties("bq")),
                List.of(new FileDatasourceProperties("csv"))
        );

        List<DatasourceApiModel> models = controller.list(null);
        assertEquals(4, models.size());
        assertEquals("bq", models.get(0).name());
        assertEquals("bigquery", models.get(0).type());

        DatasourceUpsertRequest create = new DatasourceUpsertRequest();
        create.setName("staging-pg");
        create.setType(DatasourceType.postgres);
        create.setTags(List.of("Staging", "party"));
        create.setHost("localhost");
        create.setDatabase("staging");
        create.setSchema("landing");
        DatasourceApiModel created = controller.create(create).getBody();
        assertEquals("staging-pg", created.name());
        assertEquals("landing", created.schema());
        assertEquals(List.of("staging", "party"), created.tags());
        assertEquals("data-recon", created.createdBy());
        assertEquals(1, created.version());
        assertEquals(true, created.active());

        DatasourceUpsertRequest update = new DatasourceUpsertRequest();
        update.setHost("127.0.0.1");
        update.setDatabase("staging2");
        DatasourceApiModel updated = controller.update("staging-pg", update);
        assertEquals(2, updated.version());
        assertEquals("landing", updated.schema());
        assertEquals("data-recon", updated.updatedBy());
        assertEquals(true, updated.active());

        assertEquals(1, controller.list("party").size());
        assertEquals("staging-pg", controller.list("party").get(0).name());
        assertEquals(HttpStatus.NO_CONTENT, controller.delete("staging-pg").getStatusCode());
        assertTrue(controller.list("party").isEmpty());
    }

    private static DatasourceController controller(
            List<PostgresDatasourceProperties> postgres,
            List<MongoDatasourceProperties> mongo,
            List<BigQueryDatasourceProperties> bigquery,
            List<FileDatasourceProperties> files) {
        DatasourceCatalog catalog = new DatasourceCatalog(postgres, mongo, bigquery, files);
        return new DatasourceController(new DatasourceRegistry(
                catalog,
                new PostgresConnectionFactoryCatalog(postgres),
                new MongoClientCatalog(mongo),
                new CalciteBigQueryCatalog(bigquery),
                new CalciteFileCatalog(files)
        ));
    }
}
