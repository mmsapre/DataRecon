package com.mms.data.recon.dataset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatasetRecSchedulerTest {

    @Test
    void parsesDurationSuffixes() {
        assertEquals(1, DatasetRecScheduler.parseSeconds("1500ms"));
        assertEquals(60, DatasetRecScheduler.parseSeconds("60s"));
        assertEquals(300, DatasetRecScheduler.parseSeconds("5m"));
        assertEquals(3600, DatasetRecScheduler.parseSeconds("1h"));
        assertEquals(42, DatasetRecScheduler.parseSeconds("42"));
    }
}
