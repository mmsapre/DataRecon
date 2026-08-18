package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class MongoDocumentMapper {

    private MongoDocumentMapper() {}

    public static DataLoadDefinition.RawRow toRawRow(
            Document document,
            String migrationKeyField,
            List<String> fields) {
        return toRawRow(document, MigrationKeySpec.single(migrationKeyField), fields);
    }

    public static DataLoadDefinition.RawRow toRawRow(
            Document document,
            MigrationKeySpec migrationKey,
            List<String> fields) {

        if (fields == null || fields.isEmpty()) {
            throw new ConfigurationException(
                    "Mongo load definitions must set `fields` in the same order as the PostgreSQL "
                            + "SELECT columns after MigrationKey"
            );
        }
        if (migrationKey == null) {
            throw new ConfigurationException("Mongo load definitions must set `migrationKey`");
        }
        migrationKey.initialize();
        List<String> keyColumns = migrationKey.resolvedColumns();
        if (keyColumns.isEmpty()) {
            throw new ConfigurationException(
                    "Mongo does not support DEFINED SQL expressions; use SINGLE, COMPOSITE, "
                            + "or a field-path DEFINED expression"
            );
        }

        List<String> columns = new ArrayList<>(fields.size() + 1);
        List<Object> values = new ArrayList<>(fields.size() + 1);

        columns.add(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME);
        List<Object> keyParts = new ArrayList<>(keyColumns.size());
        for (String column : keyColumns) {
            keyParts.add(convert(read(document, column)));
        }
        values.add(migrationKey.compose(keyParts));

        for (String field : fields) {
            columns.add(field);
            values.add(convert(read(document, field)));
        }

        return new DataLoadDefinition.RawRow(columns, values);
    }

    static Object read(Document document, String path) {
        if (document == null || path == null || path.isBlank()) {
            return null;
        }

        Object current = document;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Document nested)) {
                return null;
            }
            current = nested.get(part);
        }
        return current;
    }

    static Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Binary binary) {
            return java.util.HexFormat.of().formatHex(binary.getData());
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(convert(item));
            }
            return converted.toString();
        }
        return value;
    }
}
