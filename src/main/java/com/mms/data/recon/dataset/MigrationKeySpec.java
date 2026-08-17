package com.mms.data.recon.dataset;

import com.mms.data.recon.config.SqlIdentifiers;
import io.micronaut.context.exceptions.ConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * How a profile (or a source/target side) produces MigrationKey.
 * {@code type} is the supported enum: SINGLE, COMPOSITE, or DEFINED.
 */
public class MigrationKeySpec {

    public static final String DEFAULT_SEPARATOR = "|";

    private MigrationKeyType type;
    private List<String> columns = new ArrayList<>();
    private String expression;
    private String separator = DEFAULT_SEPARATOR;

    public static MigrationKeySpec single(String column) {
        MigrationKeySpec spec = new MigrationKeySpec();
        spec.setType(MigrationKeyType.SINGLE);
        spec.setColumns(column == null ? List.of() : List.of(column));
        spec.initialize();
        return spec;
    }

    public static MigrationKeySpec composite(List<String> columns) {
        return composite(columns, DEFAULT_SEPARATOR);
    }

    public static MigrationKeySpec composite(List<String> columns, String separator) {
        MigrationKeySpec spec = new MigrationKeySpec();
        spec.setType(MigrationKeyType.COMPOSITE);
        spec.setColumns(columns);
        spec.setSeparator(separator);
        spec.initialize();
        return spec;
    }

    public static MigrationKeySpec defined(String expression) {
        MigrationKeySpec spec = new MigrationKeySpec();
        spec.setType(MigrationKeyType.DEFINED);
        spec.setExpression(expression);
        spec.initialize();
        return spec;
    }

    public void initialize() {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        if (separator == null || separator.isEmpty()) {
            separator = DEFAULT_SEPARATOR;
        }
        if (type == null) {
            type = inferType();
        }
        switch (type) {
            case SINGLE -> {
                if (columns.size() != 1 || columns.get(0) == null || columns.get(0).isBlank()) {
                    throw new ConfigurationException(
                            "migrationKey type SINGLE requires exactly one column"
                    );
                }
                SqlIdentifiers.require("migrationKey.columns", columns.get(0));
            }
            case COMPOSITE -> {
                if (columns.size() < 2) {
                    throw new ConfigurationException(
                            "migrationKey type COMPOSITE requires two or more columns"
                    );
                }
                for (String column : columns) {
                    SqlIdentifiers.require("migrationKey.columns", column);
                }
            }
            case DEFINED -> {
                if (expression == null || expression.isBlank()) {
                    throw new ConfigurationException(
                            "migrationKey type DEFINED requires expression"
                    );
                }
                String trimmed = expression.trim();
                if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*")) {
                    throw new ConfigurationException(
                            "migrationKey DEFINED expression cannot contain statement separators or comments"
                    );
                }
                expression = trimmed;
            }
        }
    }

    private MigrationKeyType inferType() {
        if (expression != null && !expression.isBlank()) {
            return MigrationKeyType.DEFINED;
        }
        if (columns.size() > 1) {
            return MigrationKeyType.COMPOSITE;
        }
        return MigrationKeyType.SINGLE;
    }

    public String sqlExpression(DatasourceType datasourceType) {
        initialize();
        return switch (type) {
            case SINGLE -> SqlIdentifiers.require("migrationKey.columns", columns.get(0));
            case COMPOSITE -> compositeSql(datasourceType);
            case DEFINED -> expression;
        };
    }

    public String compose(List<Object> parts) {
        initialize();
        if (type == MigrationKeyType.DEFINED) {
            if (parts == null || parts.isEmpty() || parts.get(0) == null) {
                return null;
            }
            return String.valueOf(parts.get(0));
        }
        if (parts == null || parts.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "Expected " + columns.size() + " migration key parts, got "
                            + (parts == null ? 0 : parts.size())
            );
        }
        for (Object part : parts) {
            if (part == null) {
                return null;
            }
        }
        return parts.stream().map(String::valueOf).collect(Collectors.joining(separator));
    }

    public List<String> resolvedColumns() {
        initialize();
        if (type == MigrationKeyType.DEFINED) {
            return expressionLooksLikeFieldPath() ? List.of(expression) : List.of();
        }
        return List.copyOf(columns);
    }

    public boolean expressionLooksLikeFieldPath() {
        return expression != null && expression.matches("[A-Za-z_][A-Za-z0-9_.]*");
    }

    private String compositeSql(DatasourceType datasourceType) {
        String sep = sqlString(separator);
        if (datasourceType == DatasourceType.bigquery) {
            return columns.stream()
                    .map(column -> "CAST(" + SqlIdentifiers.require("migrationKey.columns", column) + " AS STRING)")
                    .collect(Collectors.joining(", " + sep + ", ", "CONCAT(", ")"));
        }
        return columns.stream()
                .map(column -> "CAST(" + SqlIdentifiers.require("migrationKey.columns", column) + " AS TEXT)")
                .collect(Collectors.joining(" || " + sep + " || "));
    }

    private static String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    public MigrationKeyType getType() { return type; }
    public void setType(MigrationKeyType type) { this.type = type; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) {
        this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
    }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getSeparator() { return separator; }
    public void setSeparator(String separator) { this.separator = separator; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MigrationKeySpec that)) return false;
        return type == that.type
                && Objects.equals(columns, that.columns)
                && Objects.equals(expression, that.expression)
                && Objects.equals(separator, that.separator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, columns, expression, separator);
    }
}
