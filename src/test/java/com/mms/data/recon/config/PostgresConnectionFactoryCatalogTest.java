package com.mms.data.recon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgresConnectionFactoryCatalogTest {

    @Test
    void readsSchemaFromUriQueryParams() {
        assertEquals(
                "landing",
                PostgresConnectionFactoryCatalog.schemaFromUrl(
                        "r2dbc:postgresql://localhost:5432/data?schema=landing"
                )
        );
        assertEquals(
                "master",
                PostgresConnectionFactoryCatalog.schemaFromUrl(
                        "r2dbc:postgresql://localhost:5432/data?currentSchema=master"
                )
        );
        assertEquals(
                "landing",
                PostgresConnectionFactoryCatalog.schemaFromUrl(
                        "r2dbc:postgresql://localhost:5432/data?search_path=landing,public"
                )
        );
        assertNull(PostgresConnectionFactoryCatalog.schemaFromUrl(
                "r2dbc:postgresql://localhost:5432/data"
        ));
    }

    @Test
    void appendsSchemaWhenMissingFromUri() {
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data?schema=landing",
                PostgresConnectionFactoryCatalog.withSchemaQueryParam(
                        "r2dbc:postgresql://localhost:5432/data",
                        "landing"
                )
        );
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data?schema=landing",
                PostgresConnectionFactoryCatalog.withSchemaQueryParam(
                        "r2dbc:postgresql://localhost:5432/data?schema=landing",
                        "other"
                )
        );
    }
}
