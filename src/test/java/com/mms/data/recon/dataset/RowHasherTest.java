package com.mms.data.recon.dataset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RowHasherTest {

    @Test
    void lenientNumbersWithDifferentJvmTypesMatch() {
        String a = RowHasher.hash(List.of(1), HashingStrategy.TypeLenient);
        String b = RowHasher.hash(List.of(1L), HashingStrategy.TypeLenient);
        assertEquals(a, b);
    }

    @Test
    void strictNumbersWithDifferentJvmTypesDoNotMatch() {
        String a = RowHasher.hash(List.of(1), HashingStrategy.TypeStrict);
        String b = RowHasher.hash(List.of(1L), HashingStrategy.TypeStrict);
        assertNotEquals(a, b);
    }
}
