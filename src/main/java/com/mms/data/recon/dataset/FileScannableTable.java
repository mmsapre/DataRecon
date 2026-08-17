package com.mms.data.recon.dataset;

import com.mms.data.recon.config.FileDatasourceProperties;
import com.mms.data.recon.config.FileFormat;
import io.micronaut.context.exceptions.ConfigurationException;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Calcite table over CSV or XLSX files matched by path + name pattern.
 * Multiple matching files are UNION ALL'd as one table.
 */
public final class FileScannableTable extends AbstractTable implements ScannableTable {

    private final FileDatasourceProperties properties;

    public FileScannableTable(FileDatasourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        List<String> headers = headers();
        RelDataType varchar = typeFactory.createTypeWithNullability(
                typeFactory.createSqlType(SqlTypeName.VARCHAR),
                true
        );
        List<RelDataType> types = headers.stream().map(ignored -> varchar).toList();
        return typeFactory.createStructType(types, headers);
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        List<String> headers = headers();
        List<Path> files = properties.matchingFiles();
        return new AbstractEnumerable<>() {
            @Override
            public Enumerator<Object[]> enumerator() {
                return new FileEnumerator(properties, files, headers);
            }
        };
    }

    List<String> headers() {
        List<Path> files = properties.matchingFiles();
        try {
            return properties.resolveFormat() == FileFormat.xlsx
                    ? excelHeaders(files.get(0), properties)
                    : csvHeaders(files.get(0), properties);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Unable to read headers for file datasource [" + properties.getName() + "]",
                    e
            );
        }
    }

    static List<String> csvHeaders(Path file, FileDatasourceProperties properties) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = skipBom(reader.readLine());
            if (line == null) {
                throw new ConfigurationException("CSV file is empty: " + file);
            }
            List<String> cells = parseCsvLine(line, properties.delimiterChar());
            if (!properties.isHeader()) {
                List<String> generated = new ArrayList<>(cells.size());
                for (int i = 0; i < cells.size(); i++) {
                    generated.add("COL" + (i + 1));
                }
                return generated;
            }
            return sanitizeHeaders(cells);
        }
    }

    static List<String> excelHeaders(Path file, FileDatasourceProperties properties) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            Sheet sheet = sheet(workbook, properties.getSheet());
            Row row = sheet.getRow(sheet.getFirstRowNum());
            if (row == null) {
                throw new ConfigurationException("Excel sheet has no header row: " + file);
            }
            DataFormatter formatter = new DataFormatter();
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < row.getLastCellNum(); i++) {
                cells.add(formatter.formatCellValue(row.getCell(i)));
            }
            if (!properties.isHeader()) {
                List<String> generated = new ArrayList<>(cells.size());
                for (int i = 0; i < cells.size(); i++) {
                    generated.add("COL" + (i + 1));
                }
                return generated;
            }
            return sanitizeHeaders(cells);
        }
    }

    static Sheet sheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return workbook.getSheetAt(0);
        }
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new ConfigurationException("Excel sheet not found: " + sheetName);
        }
        return sheet;
    }

    static List<String> sanitizeHeaders(List<String> raw) {
        List<String> headers = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String value = raw.get(i) == null ? "" : raw.get(i).trim();
            if (value.isBlank()) {
                value = "COL" + (i + 1);
            }
            value = value.replaceAll("[^A-Za-z0-9_]", "_");
            if (!Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
                value = "C_" + value;
            }
            headers.add(value);
        }
        return headers;
    }

    static String skipBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    static List<String> parseCsvLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    static final class FileEnumerator implements Enumerator<Object[]> {
        private final FileDatasourceProperties properties;
        private final List<Path> files;
        private final List<String> headers;
        private int fileIndex = -1;
        private Iterator<Object[]> rows = List.<Object[]>of().iterator();
        private Object[] current;
        private Workbook workbook;

        FileEnumerator(FileDatasourceProperties properties, List<Path> files, List<String> headers) {
            this.properties = properties;
            this.files = files;
            this.headers = headers;
        }

        @Override
        public Object[] current() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            return current;
        }

        @Override
        public boolean moveNext() {
            while (true) {
                if (rows.hasNext()) {
                    current = rows.next();
                    return true;
                }
                closeWorkbook();
                fileIndex++;
                if (fileIndex >= files.size()) {
                    current = null;
                    return false;
                }
                rows = readFile(files.get(fileIndex)).iterator();
            }
        }

        @Override
        public void reset() {
            closeWorkbook();
            fileIndex = -1;
            rows = List.<Object[]>of().iterator();
            current = null;
        }

        @Override
        public void close() {
            closeWorkbook();
        }

        private void closeWorkbook() {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException ignored) {
                    // closing enumerator
                }
                workbook = null;
            }
        }

        private List<Object[]> readFile(Path file) {
            try {
                return properties.resolveFormat() == FileFormat.xlsx
                        ? readExcel(file)
                        : readCsv(file);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read " + file, e);
            }
        }

        private List<Object[]> readCsv(Path file) throws IOException {
            List<Object[]> result = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line = skipBom(reader.readLine());
                if (properties.isHeader()) {
                    line = reader.readLine();
                }
                while (line != null) {
                    if (!line.isBlank()) {
                        result.add(pad(parseCsvLine(line, properties.delimiterChar())));
                    }
                    line = reader.readLine();
                }
            }
            return result;
        }

        private List<Object[]> readExcel(Path file) throws IOException {
            workbook = WorkbookFactory.create(file.toFile(), null, true);
            Sheet sheet = sheet(workbook, properties.getSheet());
            DataFormatter formatter = new DataFormatter();
            List<Object[]> result = new ArrayList<>();
            int first = sheet.getFirstRowNum();
            int start = properties.isHeader() ? first + 1 : first;
            for (int r = start; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>(headers.size());
                for (int c = 0; c < headers.size(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                if (cells.stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                result.add(pad(cells));
            }
            return result;
        }

        private Object[] pad(List<String> cells) {
            Object[] values = new Object[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                String value = i < cells.size() ? cells.get(i) : null;
                values[i] = value == null || value.isBlank() ? null : value;
            }
            return values;
        }
    }
}
