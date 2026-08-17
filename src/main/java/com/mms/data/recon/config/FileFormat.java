package com.mms.data.recon.config;

public enum FileFormat {
    csv,
    xlsx;

    public static FileFormat fromName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("csv".equals(normalized)) {
            return csv;
        }
        if ("xlsx".equals(normalized) || "xls".equals(normalized) || "excel".equals(normalized)) {
            return xlsx;
        }
        throw new IllegalArgumentException("Unsupported file format [" + value + "]. Use csv or xlsx");
    }

    public boolean matches(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return switch (this) {
            case csv -> lower.endsWith(".csv");
            case xlsx -> lower.endsWith(".xlsx") || lower.endsWith(".xls");
        };
    }
}
