package com.mms.data.recon.api;

public record ProfileRunTriggerApiModel(
        String domainId,
        String profileId,
        long runId) {}
