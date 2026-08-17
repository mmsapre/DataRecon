package com.mms.data.recon.api;

import java.util.List;

public record DomainRunDetailApiModel(
        RunApiModel domain,
        List<RunApiModel> profiles) {}
