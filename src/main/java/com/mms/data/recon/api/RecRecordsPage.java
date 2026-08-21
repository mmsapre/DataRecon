package com.mms.data.recon.api;

import com.mms.data.recon.recrun.RecRecordRepository;

import java.util.List;

/**
 * Paginated run records response.
 */
public record RecRecordsPage(
        long runId,
        String status,
        int limit,
        int offset,
        long total,
        int pageSize,
        List<RecRecordRepository.RecRecord> records
) {}
