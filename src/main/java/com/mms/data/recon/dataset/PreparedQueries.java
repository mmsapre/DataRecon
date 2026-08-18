package com.mms.data.recon.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mms.data.recon.config.ConfigurationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared helpers for configurable queries with positional {@code ?} placeholders
 * (SQL prepared statements / Mongo JSON filter binding).
 */
public final class PreparedQueries {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PreparedQueries() {}

    public static List<Object> params(DataLoadDefinition definition) {
        List<Object> params = definition.getQueryParams();
        return params == null ? List.of() : Collections.unmodifiableList(params);
    }

    /**
     * Convert JDBC-style {@code ?} placeholders to PostgreSQL R2DBC {@code $1}, {@code $2}, …
     * String literals and quoted identifiers are left untouched.
     */
    public static String toPostgresPlaceholders(String sql) {
        if (sql == null || sql.isEmpty() || sql.indexOf('?') < 0) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length() + 8);
        boolean inSingle = false;
        boolean inDouble = false;
        int index = 1;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                out.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                out.append(c);
                continue;
            }
            if (c == '?' && !inSingle && !inDouble) {
                out.append('$').append(index++);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static int countSqlPlaceholders(String sql) {
        if (sql == null || sql.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (c == '?' && !inSingle && !inDouble) {
                count++;
            }
        }
        return count;
    }

    public static void requireParamCount(String label, int placeholders, List<Object> params) {
        int provided = params == null ? 0 : params.size();
        if (placeholders != provided) {
            throw new ConfigurationException(
                    label + " has " + placeholders + " ? placeholder(s) but queryParams has " + provided + " value(s)"
            );
        }
    }

    /**
     * Replace each JSON string placeholder {@code "?"} with the next {@code queryParams} value
     * (JSON-encoded). Unquoted {@code ?} tokens outside strings are also bound.
     */
    public static String bindMongoFilter(String filterJson, List<Object> params) {
        String json = filterJson == null || filterJson.isBlank() ? "{}" : filterJson.trim();
        List<Object> values = params == null ? List.of() : params;
        if (values.isEmpty() && json.indexOf('?') < 0) {
            return json;
        }

        StringBuilder out = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escape = false;
        int index = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    out.append(c);
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    out.append(c);
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    // closing quote of a string that might be the "?" placeholder
                    inString = false;
                    out.append(c);
                    continue;
                }
                out.append(c);
                continue;
            }

            if (c == '"') {
                // Lookahead for "?" placeholder: "?" 
                if (i + 2 < json.length() && json.charAt(i + 1) == '?' && json.charAt(i + 2) == '"') {
                    if (index >= values.size()) {
                        throw new ConfigurationException(
                                "Mongo query has more ? placeholders than queryParams values"
                        );
                    }
                    out.append(toJson(values.get(index++)));
                    i += 2;
                    continue;
                }
                inString = true;
                out.append(c);
                continue;
            }

            if (c == '?') {
                if (index >= values.size()) {
                    throw new ConfigurationException(
                            "Mongo query has more ? placeholders than queryParams values"
                    );
                }
                out.append(toJson(values.get(index++)));
                continue;
            }

            out.append(c);
        }

        if (index != values.size()) {
            throw new ConfigurationException(
                    "Mongo query has " + index + " ? placeholder(s) but queryParams has " + values.size() + " value(s)"
            );
        }
        return out.toString();
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ConfigurationException("Unable to encode Mongo queryParams value: " + value, e);
        }
    }

    public static List<Object> copy(List<Object> params) {
        return params == null ? new ArrayList<>() : new ArrayList<>(params);
    }
}
