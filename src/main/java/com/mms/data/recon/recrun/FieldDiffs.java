package com.mms.data.recon.recrun;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class FieldDiffs {

    private FieldDiffs() {}

    public static String toJson(LinkedHashMap<String, String> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return null;
        }
        return diffs.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    static LinkedHashMap<String, String> parse(String json) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank() || json.length() < 2) {
            return out;
        }
        String body = json.substring(1, json.length() - 1);
        if (body.isBlank()) {
            return out;
        }
        for (String part : body.split(",")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            out.put(unquote(pair[0]), unquote(pair[1]));
        }
        return out;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return trimmed;
    }

    static Map<String, String> asMap(String json) {
        return parse(json);
    }
}
