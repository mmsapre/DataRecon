package com.mms.data.recon.dataset;

import com.mms.data.recon.config.FileDatasourceProperties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalciteFileRowLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void csvPathAndPatternAreQueryableThroughCalcite() throws Exception {
        Files.writeString(
                tempDir.resolve("party_20240101.csv"),
                "party_id,party_name,country_code,status\nP1,Acme,US,ACTIVE\n"
        );
        Files.writeString(
                tempDir.resolve("party_20240102.csv"),
                "party_id,party_name,country_code,status\nP2,Beta,IN,ACTIVE\n"
        );

        FileDatasourceProperties properties = new FileDatasourceProperties("csv");
        properties.setPath(tempDir.toString());
        properties.setPattern("party*.csv");
        properties.setFormat("csv");
        properties.setTable("party");

        try (Connection connection = CalciteConnections.file(properties)) {
            List<DataLoadDefinition.RawRow> rows = CalciteRowLoader.query(
                    connection,
                    "SELECT party_id AS \"MigrationKey\", party_name, country_code, status FROM party ORDER BY party_id"
            );
            assertEquals(2, rows.size());
            assertEquals("P1", rows.get(0).migrationKey());
            assertEquals(List.of("Acme", "US", "ACTIVE"), rows.get(0).comparableValues());
            assertEquals("P2", rows.get(1).migrationKey());
        }
    }

    @Test
    void xlsxPathAndPatternAreQueryableThroughCalcite() throws Exception {
        Path file = tempDir.resolve("party_master.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("party");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("party_id");
            header.createCell(1).setCellValue("party_name");
            header.createCell(2).setCellValue("country_code");
            header.createCell(3).setCellValue("status");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("P1");
            row.createCell(1).setCellValue("Acme");
            row.createCell(2).setCellValue("US");
            row.createCell(3).setCellValue("ACTIVE");
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        FileDatasourceProperties properties = new FileDatasourceProperties("xlsx");
        properties.setPath(tempDir.toString());
        properties.setPattern("party*.xlsx");
        properties.setFormat("xlsx");
        properties.setTable("party");
        properties.setSheet("party");

        try (Connection connection = CalciteConnections.file(properties)) {
            List<DataLoadDefinition.RawRow> rows = CalciteRowLoader.query(
                    connection,
                    "SELECT party_id AS \"MigrationKey\", party_name, country_code, status FROM party"
            );
            assertEquals(1, rows.size());
            assertEquals("P1", rows.get(0).migrationKey());
            assertEquals(List.of("Acme", "US", "ACTIVE"), rows.get(0).comparableValues());
        }
    }
}
