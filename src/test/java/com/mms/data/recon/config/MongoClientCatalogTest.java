package com.mms.data.recon.config;

import com.mms.data.recon.api.DatasourceUpsertRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MongoClientCatalogTest {

    @Test
    void registerRequiresUri() {
        MongoClientCatalog catalog = new MongoClientCatalog();
        MongoDatasourceProperties props = new MongoDatasourceProperties("orders");
        assertThrows(ConfigurationException.class, () -> catalog.register(props));
    }

    @Test
    void toMongoAcceptsUrlAlias() {
        DatasourceUpsertRequest request = new DatasourceUpsertRequest();
        request.setUrl("mongodb://db.example:27017/orders");
        request.setDatabase("orders");
        MongoDatasourceProperties props = request.toMongo("orders");
        assertEquals("mongodb://db.example:27017/orders", props.getUri());
        assertEquals("orders", props.getDatabase());
    }
}
