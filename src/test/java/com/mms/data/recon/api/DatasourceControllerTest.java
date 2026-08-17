package com.mms.data.recon.api;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.MongoDatasourceProperties;
import com.mms.data.recon.config.PostgresDatasourceProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatasourceControllerTest {

    @Test
    void listsDefinedDatasources() {
        DatasourceCatalog catalog = new DatasourceCatalog(
                List.of(new PostgresDatasourceProperties("landing")),
                List.of(new MongoDatasourceProperties("mongo")),
                List.of(new BigQueryDatasourceProperties("bq")),
                List.of(new FileDatasourceProperties("csv"))
        );

        List<DatasourceApiModel> models = new DatasourceController(catalog).list();
        assertEquals(4, models.size());
        assertEquals("bq", models.get(0).name());
        assertEquals("bigquery", models.get(0).type());
        assertEquals("csv", models.get(1).name());
        assertEquals("file", models.get(1).type());
        assertEquals("landing", models.get(2).name());
        assertEquals("postgres", models.get(2).type());
        assertEquals("mongo", models.get(3).name());
        assertEquals("mongo", models.get(3).type());
    }
}
