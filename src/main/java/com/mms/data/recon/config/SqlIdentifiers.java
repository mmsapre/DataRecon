package com.mms.data.recon.config;

public final class SqlIdentifiers {

    private SqlIdentifiers() {}

    public static String require(String field, String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                    field + " must be a simple SQL identifier, got: " + value
            );
        }
        return value;
    }

    public static String qualifyTable(String schema, String table) {
        String qualifiedTable = require("table", table);
        if (schema == null || schema.isBlank()) {
            return qualifiedTable;
        }
        return require("schema", schema) + "." + qualifiedTable;
    }
}
