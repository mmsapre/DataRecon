package com.mms.data.recon.api;

import java.util.List;

public record DomainApiModel(
        String id,
        String schedule,
        String hashingStrategy,
        List<String> tags,
        List<ProfileApiModel> profiles) {}
