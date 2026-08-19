package com.mms.data.recon.config;

import io.r2dbc.spi.ConnectionFactoryOptions;
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

    @Test
    void normalizesJdbcAndBarePostgresUrlsToR2dbc() {
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data",
                PostgresConnectionFactoryCatalog.toR2dbcUrl("jdbc:postgresql://localhost:5432/data")
        );
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data",
                PostgresConnectionFactoryCatalog.toR2dbcUrl("postgresql://localhost:5432/data")
        );
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data",
                PostgresConnectionFactoryCatalog.toR2dbcUrl("postgres://localhost:5432/data")
        );
        assertEquals(
                "r2dbc:postgresql://localhost:5432/data",
                PostgresConnectionFactoryCatalog.toR2dbcUrl("r2dbc:postgresql://localhost:5432/data")
        );
    }

    @Test
    void appliesUsernamePasswordWhenMissingFromUrl() {
        PostgresDatasourceProperties props = new PostgresDatasourceProperties("landing");
        props.setUsername("recon");
        props.setPassword("secret");
        ConnectionFactoryOptions options = PostgresConnectionFactoryCatalog.optionsFromUrl(
                props,
                "r2dbc:postgresql://localhost:5432/data",
                null
        );
        assertEquals("recon", options.getRequiredValue(ConnectionFactoryOptions.USER));
        assertEquals("secret", options.getRequiredValue(ConnectionFactoryOptions.PASSWORD));
    }
}
