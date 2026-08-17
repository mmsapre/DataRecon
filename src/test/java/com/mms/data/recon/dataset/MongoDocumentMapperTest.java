package com.mms.data.recon.dataset;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoDocumentMapperTest {

    @Test
    void aliasesConfiguredKeyAndPreservesFieldOrder() {
        Document document = new Document()
                .append("_id", new ObjectId())
                .append("party_id", "P1")
                .append("status", "ACTIVE")
                .append("party_name", "Acme")
                .append("country_code", "US");

        DataLoadDefinition.RawRow row = MongoDocumentMapper.toRawRow(
                document,
                "party_id",
                List.of("party_name", "country_code", "status")
        );

        assertEquals("P1", row.migrationKey());
        assertEquals(List.of("Acme", "US", "ACTIVE"), row.comparableValues());
    }

    @Test
    void postgresAndMongoComparableValuesHashTheSame() {
        Document document = new Document()
                .append("party_id", 1)
                .append("party_name", "Acme")
                .append("country_code", "US")
                .append("amount", Decimal128.parse("10.50"));

        DataLoadDefinition.RawRow mongo = MongoDocumentMapper.toRawRow(
                document,
                "party_id",
                List.of("party_name", "country_code", "amount")
        );

        String mongoHash = RowHasher.hash(mongo.comparableValues(), HashingStrategy.TypeLenient);
        String postgresHash = RowHasher.hash(
                List.of("Acme", "US", new BigDecimal("10.50")),
                HashingStrategy.TypeLenient
        );

        assertEquals("1", mongo.migrationKey());
        assertEquals(postgresHash, mongoHash);
    }

    @Test
    void readsNestedFields() {
        Document document = new Document()
                .append("party_id", "P1")
                .append("address", new Document("country_code", "US"));

        DataLoadDefinition.RawRow row = MongoDocumentMapper.toRawRow(
                document,
                "party_id",
                List.of("address.country_code")
        );

        assertEquals(List.of("US"), row.comparableValues());
    }

    @Test
    void composesCompositeMigrationKey() {
        Document document = new Document()
                .append("party_id", "P1")
                .append("country_code", "US")
                .append("party_name", "Acme");

        DataLoadDefinition.RawRow row = MongoDocumentMapper.toRawRow(
                document,
                MigrationKeySpec.composite(List.of("party_id", "country_code")),
                List.of("party_name")
        );

        assertEquals("P1|US", row.migrationKey());
        assertEquals(List.of("Acme"), row.comparableValues());
    }
}
