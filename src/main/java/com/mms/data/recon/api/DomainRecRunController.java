package com.mms.data.recon.api;

import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.ReconMode;
import com.mms.data.recon.dataset.ReconSettings;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import com.mms.data.recon.recrun.RecRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Runs")
@RestController
@RequestMapping("/api")
public class DomainRecRunController {

    private final RecRunService service;

    public DomainRecRunController(RecRunService service) {
        this.service = service;
    }

    @PostMapping("/domains/{domainId}/runs")
    @Operation(summary = "Trigger domain run (async)",
            description = "Returns immediately with status RUNNING. Poll domain/profile run GETs until COMPLETED or FAILED.")
    public Mono<ResponseEntity<DomainRunTriggerApiModel>> runDomain(
            @PathVariable String domainId,
            @Nullable @RequestBody ReconRunRequest request) {
        return service.runDomain(domainId, mode(request), fields(request), forceFull(request))
                .map(result -> ResponseEntity.accepted()
                        .body(new DomainRunTriggerApiModel(
                                result.domainId(),
                                result.domainRunId(),
                                result.runIds(),
                                "RUNNING")));
    }

    @GetMapping("/domains/{domainId}/runs")
    public List<RunApiModel> domainRuns(
            @PathVariable String domainId,
            @Nullable @RequestParam(required = false) Boolean active) {
        return service.domainRuns(domainId, active).stream().map(this::api).toList();
    }

    @GetMapping("/domains/{domainId}/runs/{domainRunId}")
    public DomainRunDetailApiModel domainRun(
            @PathVariable String domainId,
            @PathVariable long domainRunId) {
        RecRunService.DomainRunDetail detail = service.domainRun(domainId, domainRunId);
        return new DomainRunDetailApiModel(
                api(detail.domain()),
                detail.profiles().stream().map(this::api).toList()
        );
    }

    @PostMapping("/domains/{domainId}/profiles/{profileId}/runs")
    @Operation(summary = "Trigger profile run (async)",
            description = "Returns immediately with status RUNNING. Poll GET /api/runs/{runId} until COMPLETED or FAILED.")
    public Mono<ResponseEntity<ProfileRunTriggerApiModel>> runProfile(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Nullable @RequestBody ReconRunRequest request) {
        ReconMode requested = mode(request);
        return service.runProfile(domainId, profileId, requested, fields(request), forceFull(request))
                .map(id -> ResponseEntity.accepted()
                        .body(new ProfileRunTriggerApiModel(
                                domainId,
                                profileId,
                                DatasetConfiguration.qualifiedId(domainId, profileId),
                                requested == null ? null : requested.name(),
                                id,
                                "RUNNING")));
    }

    @PostMapping("/profiles/runs/counts")
    @Operation(summary = "Trigger COUNTS by profile name or id (async)",
            description = "Returns immediately with status RUNNING. Poll GET /api/runs/{runId} for COMPLETED/FAILED.")
    public Mono<ResponseEntity<ProfileRunTriggerApiModel>> runProfileCounts(
            @RequestBody ProfileTriggerRequest request) {
        return triggerByRef(request, ReconMode.COUNTS);
    }

    @PostMapping("/profiles/runs/details")
    @Operation(summary = "Trigger MISMATCH_DETAILS by profile name or id (async)",
            description = "Returns immediately with status RUNNING. Poll GET /api/runs/{runId} for COMPLETED/FAILED.")
    public Mono<ResponseEntity<ProfileRunTriggerApiModel>> runProfileDetails(
            @RequestBody ProfileTriggerRequest request) {
        return triggerByRef(request, ReconMode.MISMATCH_DETAILS);
    }

    @GetMapping("/domains/{domainId}/profiles/{profileId}/runs")
    public List<RunApiModel> profileRuns(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Nullable @RequestParam(required = false) Boolean active) {
        return service.profileRuns(domainId, profileId, active).stream().map(this::api).toList();
    }

    @PutMapping("/domains/{domainId}/recon")
    public ReconRunRequest updateDomainRecon(
            @PathVariable String domainId,
            @RequestBody ReconRunRequest request) {
        return toRequest(service.applyDomainRecon(domainId, mode(request), fields(request)));
    }

    @PutMapping("/domains/{domainId}/profiles/{profileId}/recon")
    public ReconRunRequest updateProfileRecon(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @RequestBody ReconRunRequest request) {
        return toRequest(service.applyProfileRecon(domainId, profileId, mode(request), fields(request)));
    }

    @GetMapping("/runs")
    public List<RunApiModel> runs(
            @RequestParam(defaultValue = "") String datasetId,
            @Nullable @RequestParam(required = false) Boolean active) {
        String filter = datasetId == null || datasetId.isBlank() ? null : datasetId;
        return service.runs(filter).stream()
                .filter(run -> active == null || active == run.active())
                .map(this::api)
                .toList();
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get run status",
            description = "Poll after async trigger. status is RUNNING, COMPLETED, or FAILED.")
    public RunApiModel run(@PathVariable long runId) {
        RecRunRepository.RunView view = service.run(runId);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown run: " + runId);
        }
        return api(view);
    }

    @GetMapping("/runs/{runId}/records")
    @Operation(
            summary = "List run records (paginated)",
            description = "Filter by status: MATCHED, MISMATCHED, SOURCE_ONLY|SOURCE, TARGET_ONLY|TARGET. "
                    + "Returns limit, offset, total, and pageSize (rows on this page)."
    )
    public RecRecordsPage records(
            @PathVariable long runId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            RecRecordRepository.Page page = service.recordsPage(runId, status, limit, offset);
            return new RecRecordsPage(
                    page.runId(),
                    page.status(),
                    page.limit(),
                    page.offset(),
                    page.total(),
                    page.pageSize(),
                    page.records()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Mono<ResponseEntity<ProfileRunTriggerApiModel>> triggerByRef(
            ProfileTriggerRequest request,
            ReconMode mode) {
        if (request == null || request.getProfile() == null || request.getProfile().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile is required"));
        }
        return service.runResolvedProfile(
                        request.getDomain(),
                        request.getProfile(),
                        mode,
                        request.getConditionFields(),
                        Boolean.TRUE.equals(request.getForceFull()))
                .map(result -> ResponseEntity.accepted()
                        .body(new ProfileRunTriggerApiModel(
                                result.domainId(),
                                result.profileId(),
                                result.id(),
                                result.mode().name(),
                                result.runId(),
                                "RUNNING")))
                .onErrorMap(IllegalArgumentException.class, this::statusFor);
    }

    private ResponseStatusException statusFor(IllegalArgumentException e) {
        String message = e.getMessage() == null ? "Bad request" : e.getMessage();
        HttpStatus status = message.startsWith("Unknown") ? HttpStatus.NOT_FOUND
                : message.startsWith("Ambiguous") ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, message, e);
    }

    private RunApiModel api(RecRunRepository.RunView r) {
        return new RunApiModel(
                r.id(), r.datasetId(), r.domainId(), r.profileId(), r.domainRunId(),
                r.status(), r.startedAt(), r.completedAt(),
                r.sourceCount(), r.targetCount(), r.matched(),
                r.mismatched(), r.sourceOnly(), r.targetOnly(), r.errorMessage(),
                r.active(), r.reconMode(),
                r.sourceQuery(), r.targetQuery(), r.conditionFields(),
                r.runScope(), r.baselineRunId()
        );
    }

    private static ReconMode mode(ReconRunRequest request) {
        return request == null ? null : request.getMode();
    }

    private static List<String> fields(ReconRunRequest request) {
        return request == null ? null : request.getConditionFields();
    }

    private static boolean forceFull(ReconRunRequest request) {
        return request != null && Boolean.TRUE.equals(request.getForceFull());
    }

    private static ReconRunRequest toRequest(ReconSettings recon) {
        ReconRunRequest response = new ReconRunRequest();
        response.setMode(recon.resolvedMode());
        response.setConditionFields(recon.resolvedConditionFields());
        return response;
    }
}
