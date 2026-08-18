package com.mms.data.recon.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class FileDatasourceProperties {

    private String name;
    private String path;
    private String pattern;
    private String format;
    private String table;
    private String sheet;
    private String delimiter = ",";
    private boolean header = true;
    private String calciteSchema = "files";

    public FileDatasourceProperties() {}

    public FileDatasourceProperties(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter == null || delimiter.isBlank() ? "," : delimiter;
    }

    public boolean isHeader() { return header; }
    public void setHeader(boolean header) { this.header = header; }

    public String getCalciteSchema() { return calciteSchema; }
    public void setCalciteSchema(String calciteSchema) {
        this.calciteSchema = calciteSchema == null || calciteSchema.isBlank() ? "files" : calciteSchema;
    }

    public String resolveCalciteSchema() {
        return calciteSchema == null || calciteSchema.isBlank() ? "files" : calciteSchema;
    }

    public FileFormat resolveFormat() {
        FileFormat explicit = FileFormat.fromName(format);
        if (explicit != null) {
            return explicit;
        }
        String hint = pattern == null ? "" : pattern.toLowerCase(Locale.ROOT);
        if (hint.contains("xlsx") || hint.contains("xls")) {
            return FileFormat.xlsx;
        }
        return FileFormat.csv;
    }

    public String resolveTableName() {
        if (table != null && !table.isBlank()) {
            return SqlIdentifiers.require("file.table", table);
        }
        String fromPattern = tableNameFromPattern(pattern);
        if (fromPattern != null) {
            return fromPattern;
        }
        List<Path> files = matchingFiles();
        if (!files.isEmpty()) {
            return sanitizeTableName(stripExtension(files.get(0).getFileName().toString()));
        }
        return SqlIdentifiers.require("file.datasources", name);
    }

    public Path resolvePath() {
        if (path == null || path.isBlank()) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] requires path (directory or file)"
            );
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    public List<Path> matchingFiles() {
        Path base = resolvePath();
        if (!Files.exists(base)) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] path does not exist: " + base
            );
        }
        Pattern regex = compiledPattern();
        FileFormat resolved = resolveFormat();
        if (Files.isRegularFile(base)) {
            String fileName = base.getFileName().toString();
            if (!resolved.matches(fileName) || !regex.matcher(fileName).matches()) {
                throw new ConfigurationException(
                        "File datasource [" + name + "] path [" + base + "] does not match pattern ["
                                + resolvedPattern() + "]"
                );
            }
            return List.of(base);
        }
        if (!Files.isDirectory(base)) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] path is not a file or directory: " + base
            );
        }
        String expression = resolvedPattern();
        boolean recursive = expression.contains("/");
        try (Stream<Path> stream = recursive ? Files.walk(base) : Files.list(base)) {
            List<Path> matched = stream
                    .filter(Files::isRegularFile)
                    .filter(candidate -> resolved.matches(candidate.getFileName().toString()))
                    .filter(candidate -> matchesRegex(base, candidate, regex))
                    .sorted()
                    .toList();
            if (matched.isEmpty()) {
                throw new ConfigurationException(
                        "File datasource [" + name + "] path [" + base + "] pattern [" + expression
                                + "] matched no " + resolved + " files"
                );
            }
            return matched;
        } catch (ConfigurationException e) {
            throw e;
        } catch (IOException e) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] cannot read path [" + base + "]",
                    e
            );
        }
    }

    public char delimiterChar() {
        String value = delimiter == null || delimiter.isBlank() ? "," : delimiter;
        return value.charAt(0);
    }

    Pattern compiledPattern() {
        String expression = resolvedPattern();
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] pattern is not valid regex: " + expression,
                    e
            );
        }
    }

    private String resolvedPattern() {
        if (pattern != null && !pattern.isBlank()) {
            return pattern;
        }
        return resolveFormat() == FileFormat.xlsx ? ".*[.]xlsx" : ".*[.]csv";
    }

    private static boolean matchesRegex(Path base, Path candidate, Pattern regex) {
        Path fileName = candidate.getFileName();
        if (fileName != null && regex.matcher(fileName.toString()).matches()) {
            return true;
        }
        try {
            String relative = base.relativize(candidate).toString().replace('\\', '/');
            return regex.matcher(relative).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String tableNameFromPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        String fileName = pattern.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        String stem = stripExtension(fileName)
                .replaceAll("\\\\[dDsSwW]", "")
                .replaceAll("[\\\\.^$*+?()\\[\\]{}|]+", "")
                .replace("-", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (stem.isBlank() || !stem.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        return stem;
    }

    static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    static String sanitizeTableName(String raw) {
        String cleaned = raw.replace('-', '_').replace(' ', '_');
        return SqlIdentifiers.require("file.table", cleaned);
    }
}
