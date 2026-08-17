package com.mms.data.recon.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HashingStrategyTest {

    @ParameterizedTest
    @EnumSource(HashingStrategy.class)
    void consecutiveFieldsAlwaysLeadToDifferentHashes(HashingStrategy strategy) {
        String left = RowHasher.hash(List.of("abc", "def"), strategy);
        String right = RowHasher.hash(List.of("ab", "cdef"), strategy);
        assertNotEquals(left, right);
        assertTrue(isSha256Hex(left));
        assertTrue(isSha256Hex(right));
    }

    @Test
    void lenientStrategyTreatsEquivalentNumericTypesAsEqual() {
        assertEquals(
                RowHasher.hash(List.of(10), HashingStrategy.TypeLenient),
                RowHasher.hash(List.of(10L), HashingStrategy.TypeLenient)
        );
        assertEquals(
                RowHasher.hash(List.of(10), HashingStrategy.TypeLenient),
                RowHasher.hash(List.of((short) 10), HashingStrategy.TypeLenient)
        );
        assertEquals(
                RowHasher.hash(List.of(10.0f), HashingStrategy.TypeLenient),
                RowHasher.hash(List.of(10.0d), HashingStrategy.TypeLenient)
        );
        assertEquals(
                RowHasher.hash(List.of(true), HashingStrategy.TypeLenient),
                RowHasher.hash(List.of((byte) 1), HashingStrategy.TypeLenient)
        );
        assertEquals(
                RowHasher.hash(List.of(BigDecimal.TEN), HashingStrategy.TypeLenient),
                RowHasher.hash(List.of(10L), HashingStrategy.TypeLenient)
        );
    }

    @Test
    void strictStrategyTreatsDifferentJvmTypesAsUnequal() {
        assertNotEquals(
                RowHasher.hash(List.of(10), HashingStrategy.TypeStrict),
                RowHasher.hash(List.of(10L), HashingStrategy.TypeStrict)
        );
    }

    @Test
    void lenientNullsOfDifferentDeclaredTypesHashTheSame() {
        assertEquals(
                RowHasher.hash(Collections.singletonList(null), HashingStrategy.TypeLenient),
                RowHasher.hash(Collections.singletonList(null), HashingStrategy.TypeLenient)
        );
    }

    @Test
    void strictNullsHashTheSameBecauseNullHasNoRuntimeType() {
        assertEquals(
                RowHasher.hash(Collections.singletonList(null), HashingStrategy.TypeStrict),
                RowHasher.hash(Collections.singletonList(null), HashingStrategy.TypeStrict)
        );
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy.class)
    void rawRowRejectsNullMigrationKey(HashingStrategy strategy) {
        DataLoadDefinition.RawRow row = new DataLoadDefinition.RawRow(
                List.of(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME, "col"),
                Arrays.asList(null, "x")
        );
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, row::migrationKey);
        assertTrue(error.getMessage().contains("null"));
        assertTrue(isSha256Hex(RowHasher.hash(List.of("x"), strategy)));
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy.class)
    void rawRowRejectsMissingMigrationKey(HashingStrategy strategy) {
        DataLoadDefinition.RawRow row = new DataLoadDefinition.RawRow(List.of("col"), List.of("x"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, row::migrationKey);
        assertTrue(error.getMessage().contains(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME));
        assertNotNull(strategy);
    }

    @Test
    void rawRowRejectsDuplicateMigrationKey() {
        DataLoadDefinition.RawRow row = new DataLoadDefinition.RawRow(
                List.of(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME, "col", DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME),
                List.of("k1", "x", "k2")
        );
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, row::migrationKey);
        assertTrue(error.getMessage().contains("More than one column"));
    }

    private static boolean isSha256Hex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
