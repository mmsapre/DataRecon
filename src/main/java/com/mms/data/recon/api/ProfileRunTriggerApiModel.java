package com.mms.data.recon.api;

import com.mms.data.recon.dataset.ReconMode;

public record ProfileRunTriggerApiModel(
        String domainId,
        String profileId,
        String id,
        String mode,
        long runId) {}
