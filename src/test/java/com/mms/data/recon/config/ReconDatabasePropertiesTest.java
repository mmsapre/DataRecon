package com.mms.data.recon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconDatabasePropertiesTest {

    @Test
    void defaultsToPublicRecRunAndRecRecord() {
        ReconDatabaseProperties properties = new ReconDatabaseProperties();
        assertEquals("public.rec_run", properties.qualifiedRunTable());
        assertEquals("public.rec_record", properties.qualifiedRecordTable());
        assertTrue(properties.jdbcUrl().contains("currentSchema=public"));
    }

    @Test
    void schemaAndTableNamesAreConfigurable() {
        ReconDatabaseProperties properties = new ReconDatabaseProperties();
        properties.setSchema("recon");
        properties.getTables().setRun("run_history");
        properties.getTables().setRecord("run_keys");

        assertEquals("recon.run_history", properties.qualifiedRunTable());
        assertEquals("recon.run_keys", properties.qualifiedRecordTable());
        assertTrue(properties.jdbcUrl().contains("currentSchema=recon"));
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        ReconDatabaseProperties properties = new ReconDatabaseProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setSchema("recon;drop"));
        assertThrows(IllegalArgumentException.class, () -> properties.getTables().setRun("rec_run;drop"));
    }
}
