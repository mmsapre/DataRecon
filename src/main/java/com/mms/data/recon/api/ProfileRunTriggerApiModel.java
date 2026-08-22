package com.mms.data.recon.api;

public record ProfileRunTriggerApiModel(
        String domainId,
        String profileId,
        String id,
        String mode,
        long runId,
        String status) {}
