package com.mms.data.recon.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconSchemaBootstrapTest {

    @Test
    void placeholdersIncludeSchemaAndUnqualifiedTables() {
        ReconDatabaseProperties database = new ReconDatabaseProperties();
        database.setSchema("recon");
        database.getTables().setRun("run_history");

        Map<String, String> placeholders = new ReconSchemaBootstrap(null, database).placeholders();

        assertEquals("\"recon\"", placeholders.get("schema"));
        assertEquals("\"run_history\"", placeholders.get("runTable"));
        assertEquals("\"rec_record\"", placeholders.get("recordTable"));
        assertTrue(placeholders.containsKey("datasourceTable"));
        assertTrue(placeholders.containsKey("domainTable"));
        assertTrue(placeholders.containsKey("profileTable"));
    }
}
