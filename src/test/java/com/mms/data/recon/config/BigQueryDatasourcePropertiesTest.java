package com.mms.data.recon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigQueryDatasourcePropertiesTest {

    @Test
    void usesExplicitJdbcUrlWhenSet() {
        BigQueryDatasourceProperties properties = new BigQueryDatasourceProperties("bq");
        properties.setJdbcUrl("jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=demo");
        assertEquals(
                "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=demo",
                properties.resolveJdbcUrl()
        );
    }

    @Test
    void buildsJdbcUrlFromProjectIdAndApplicationDefaultCredentials() {
        BigQueryDatasourceProperties properties = new BigQueryDatasourceProperties("bq");
        properties.setProjectId("demo-project");
        String url = properties.resolveJdbcUrl();
        assertTrue(url.contains("ProjectId=demo-project"));
        assertTrue(url.contains("OAuthType=3"));
    }

    @Test
    void buildsServiceAccountUrlWhenCredentialsFileSet() {
        BigQueryDatasourceProperties properties = new BigQueryDatasourceProperties("bq");
        properties.setProjectId("demo-project");
        properties.setCredentialsFile("/tmp/sa.json");
        String url = properties.resolveJdbcUrl();
        assertTrue(url.contains("OAuthType=0"));
        assertTrue(url.contains("OAuthPvtKeyPath=/tmp/sa.json"));
    }

    @Test
    void catalogFallsBackToProjectId() {
        BigQueryDatasourceProperties properties = new BigQueryDatasourceProperties("bq");
        properties.setProjectId("demo-project");
        assertEquals("demo-project", properties.resolveCatalog());
        properties.setCatalog("other");
        assertEquals("other", properties.resolveCatalog());
    }

    @Test
    void requiresJdbcUrlOrProjectId() {
        BigQueryDatasourceProperties properties = new BigQueryDatasourceProperties("bq");
        assertThrows(IllegalArgumentException.class, properties::resolveJdbcUrl);
    }
}
