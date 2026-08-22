package com.mms.data.recon.api;

import java.util.Map;

public record DomainRunTriggerApiModel(
        String domainId,
        long domainRunId,
        Map<String, Long> runIds,
        String status) {}
