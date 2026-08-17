package com.mms.data.recon.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mms.data.recon.dataset.ReconMode;
import com.mms.data.recon.dataset.ReconSettings;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import com.mms.data.recon.recrun.RecRunService;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Runs")
@Controller("/api")
public class DomainRecRunController {

    private final RecRunService service;

    public DomainRecRunController(RecRunService service) {
        this.service = service;
    }

    @Post("/domains/{domainId}/runs")
    public Mono<HttpResponse<DomainRunTriggerApiModel>> runDomain(
            @PathVariable String domainId,
            @Nullable @Body ReconRunRequest request) {
        return service.runDomain(domainId, mode(request), fields(request))
                .map(result -> HttpResponse.<DomainRunTriggerApiModel>accepted()
                        .body(new DomainRunTriggerApiModel(
                                result.domainId(),
                                result.domainRunId(),
                                result.runIds())));
    }

    @Get("/domains/{domainId}/runs{?active}")
    public List<RunApiModel> domainRuns(
            @PathVariable String domainId,
            @Nullable @QueryValue Boolean active) {
        return service.domainRuns(domainId, active).stream().map(this::api).toList();
    }

    @Get("/domains/{domainId}/runs/{domainRunId}")
    public DomainRunDetailApiModel domainRun(
            @PathVariable String domainId,
            @PathVariable long domainRunId) {
        RecRunService.DomainRunDetail detail = service.domainRun(domainId, domainRunId);
        return new DomainRunDetailApiModel(
                api(detail.domain()),
                detail.profiles().stream().map(this::api).toList()
        );
    }

    @Post("/domains/{domainId}/profiles/{profileId}/runs")
    public Mono<HttpResponse<ProfileRunTriggerApiModel>> runProfile(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Nullable @Body ReconRunRequest request) {
        return service.runProfile(domainId, profileId, mode(request), fields(request))
                .map(id -> HttpResponse.<ProfileRunTriggerApiModel>accepted()
                        .body(new ProfileRunTriggerApiModel(domainId, profileId, id)));
    }

    @Get("/domains/{domainId}/profiles/{profileId}/runs{?active}")
    public List<RunApiModel> profileRuns(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Nullable @QueryValue Boolean active) {
        return service.profileRuns(domainId, profileId, active).stream().map(this::api).toList();
    }

    @Put("/domains/{domainId}/recon")
    public ReconRunRequest updateDomainRecon(
            @PathVariable String domainId,
            @Body ReconRunRequest request) {
        return toRequest(service.applyDomainRecon(domainId, mode(request), fields(request)));
    }

    @Put("/domains/{domainId}/profiles/{profileId}/recon")
    public ReconRunRequest updateProfileRecon(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Body ReconRunRequest request) {
        return toRequest(service.applyProfileRecon(domainId, profileId, mode(request), fields(request)));
    }

    @Get("/runs{?datasetId,active}")
    public List<RunApiModel> runs(
            @QueryValue(defaultValue = "") String datasetId,
            @Nullable @QueryValue Boolean active) {
        String filter = datasetId == null || datasetId.isBlank() ? null : datasetId;
        return service.runs(filter).stream()
                .filter(run -> active == null || active == run.active())
                .map(this::api)
                .toList();
    }

    @Get("/runs/{runId}/records{?status}")
    public List<RecRecordRepository.RecRecord> records(
            @PathVariable long runId,
            @QueryValue(defaultValue = "") String status) {
        String filter = status == null || status.isBlank() ? null : status;
        return service.records(runId, filter);
    }

    private RunApiModel api(RecRunRepository.RunView r) {
        return new RunApiModel(
                r.id(), r.datasetId(), r.domainId(), r.profileId(), r.domainRunId(),
                r.status(), r.startedAt(), r.completedAt(),
                r.sourceCount(), r.targetCount(), r.matched(),
                r.mismatched(), r.sourceOnly(), r.targetOnly(), r.errorMessage(),
                r.active(), r.reconMode()
        );
    }

    private static ReconMode mode(ReconRunRequest request) {
        return request == null ? null : request.getMode();
    }

    private static List<String> fields(ReconRunRequest request) {
        return request == null ? null : request.getConditionFields();
    }

    private static ReconRunRequest toRequest(ReconSettings recon) {
        ReconRunRequest response = new ReconRunRequest();
        response.setMode(recon.resolvedMode());
        response.setConditionFields(recon.resolvedConditionFields());
        return response;
    }
}
