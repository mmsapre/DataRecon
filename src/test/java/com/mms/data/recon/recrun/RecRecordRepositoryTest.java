package com.mms.data.recon.recrun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecRecordRepositoryTest {

    @Test
    void normalizesSourceAndTargetAliases() {
        assertEquals("SOURCE_ONLY", RecRecordRepository.normalizeStatus("SOURCE"));
        assertEquals("SOURCE_ONLY", RecRecordRepository.normalizeStatus("source"));
        assertEquals("TARGET_ONLY", RecRecordRepository.normalizeStatus("TARGET"));
        assertEquals("MISMATCHED", RecRecordRepository.normalizeStatus("MISMATCHED"));
        assertNull(RecRecordRepository.normalizeStatus(""));
        assertNull(RecRecordRepository.normalizeStatus(null));
        assertThrows(IllegalArgumentException.class, () -> RecRecordRepository.normalizeStatus("NOPE"));
    }
}
