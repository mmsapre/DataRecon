package com.mms.data.recon.config;

import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasourcePropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void infersCsvFormatAndTableFromPattern() {
        FileDatasourceProperties properties = new FileDatasourceProperties("csv");
        properties.setPattern("party.*[.]csv");
        assertEquals(FileFormat.csv, properties.resolveFormat());
        assertEquals("party", properties.resolveTableName());
    }

    @Test
    void infersXlsxFormatFromPattern() {
        FileDatasourceProperties properties = new FileDatasourceProperties("xlsx");
        properties.setPattern("party_\\d+[.]xlsx");
        assertEquals(FileFormat.xlsx, properties.resolveFormat());
    }

    @Test
    void matchesRegexFileNamesInPath() throws Exception {
        Files.writeString(tempDir.resolve("party_20240101.csv"), "party_id\nP1\n");
        Files.writeString(tempDir.resolve("party_20240102.csv"), "party_id\nP2\n");
        Files.writeString(tempDir.resolve("other.csv"), "party_id\nX\n");

        FileDatasourceProperties properties = new FileDatasourceProperties("csv");
        properties.setPath(tempDir.toString());
        properties.setPattern("party_\\d+[.]csv");
        properties.setTable("party");

        List<Path> matched = properties.matchingFiles();
        assertEquals(2, matched.size());
        assertEquals("party", properties.resolveTableName());
    }

    @Test
    void invalidRegexFails() {
        FileDatasourceProperties properties = new FileDatasourceProperties("csv");
        properties.setPath(tempDir.toString());
        properties.setPattern("party[");
        assertThrows(ConfigurationException.class, properties::matchingFiles);
    }

    @Test
    void missingFilesFail() {
        FileDatasourceProperties properties = new FileDatasourceProperties("csv");
        properties.setPath(tempDir.toString());
        properties.setPattern("missing.*[.]csv");
        assertThrows(ConfigurationException.class, properties::matchingFiles);
    }
}
