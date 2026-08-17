package com.mms.data.recon.dataset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HashedRowTest {

    @Test
    void sourceFactorySetsSourceHash() {
        HashedRow row = HashedRow.source("k", "abc");
        assertEquals("k", row.migrationKey());
        assertEquals("abc", row.hash());
        assertEquals("abc", row.sourceHash());
        assertNull(row.targetHash());
    }

    @Test
    void withTargetKeepsSourceHash() {
        HashedRow row = HashedRow.source("k", "abc").withTarget("def");
        assertEquals("abc", row.sourceHash());
        assertEquals("def", row.targetHash());
    }
}
