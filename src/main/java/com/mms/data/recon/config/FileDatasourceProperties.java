package com.mms.data.recon.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.ConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@EachProperty(MmsRecon.PREFIX + ".file.datasources")
public class FileDatasourceProperties {

    private final String name;
    private String path;
    private String pattern;
    private String format;
    private String table;
    private String sheet;
    private String delimiter = ",";
    private boolean header = true;
    private String calciteSchema = "files";

    public FileDatasourceProperties(@Parameter String name) {
        this.name = name;
    }

    public String getName() { return name; }

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
        if (hint.contains(".xlsx") || hint.contains(".xls")) {
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
        if (Files.isRegularFile(base)) {
            if (!resolveFormat().matches(base.getFileName().toString())) {
                throw new ConfigurationException(
                        "File datasource [" + name + "] path [" + base + "] is not a "
                                + resolveFormat() + " file"
                );
            }
            return List.of(base);
        }
        if (!Files.isDirectory(base)) {
            throw new ConfigurationException(
                    "File datasource [" + name + "] path is not a file or directory: " + base
            );
        }
        String glob = resolvedPattern();
        PathMatcher matcher = base.getFileSystem().getPathMatcher("glob:" + glob);
        boolean recursive = glob.contains("**") || glob.contains("/") || glob.contains("\\");
        FileFormat resolved = resolveFormat();
        try (Stream<Path> stream = recursive ? Files.walk(base) : Files.list(base)) {
            List<Path> matched = stream
                    .filter(Files::isRegularFile)
                    .filter(candidate -> matchesGlob(base, candidate, matcher))
                    .filter(candidate -> resolved.matches(candidate.getFileName().toString()))
                    .sorted()
                    .toList();
            if (matched.isEmpty()) {
                throw new ConfigurationException(
                        "File datasource [" + name + "] path [" + base + "] pattern [" + glob
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

    private String resolvedPattern() {
        if (pattern != null && !pattern.isBlank()) {
            return pattern;
        }
        return resolveFormat() == FileFormat.xlsx ? "*.xlsx" : "*.csv";
    }

    private static boolean matchesGlob(Path base, Path candidate, PathMatcher matcher) {
        Path fileName = candidate.getFileName();
        if (fileName != null && matcher.matches(fileName)) {
            return true;
        }
        try {
            return matcher.matches(base.relativize(candidate));
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
                .replace("**", "")
                .replace("*", "")
                .replace("?", "")
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
