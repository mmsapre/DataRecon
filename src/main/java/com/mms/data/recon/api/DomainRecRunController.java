package com.mms.data.recon.api;

import com.mms.data.recon.dataset.ReconMode;
import com.mms.data.recon.dataset.ReconSettings;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import com.mms.data.recon.recrun.RecRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public Mono<ResponseEntity<DomainRunTriggerApiModel>> runDomain(
            @PathVariable String domainId,
            @Nullable @RequestBody ReconRunRequest request) {
        return service.runDomain(domainId, mode(request), fields(request))
                .map(result -> ResponseEntity.accepted()
                        .body(new DomainRunTriggerApiModel(
                                result.domainId(),
                                result.domainRunId(),
                                result.runIds())));
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
    public Mono<ResponseEntity<ProfileRunTriggerApiModel>> runProfile(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Nullable @RequestBody ReconRunRequest request) {
        return service.runProfile(domainId, profileId, mode(request), fields(request))
                .map(id -> ResponseEntity.accepted()
                        .body(new ProfileRunTriggerApiModel(domainId, profileId, id)));
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

    @GetMapping("/runs/{runId}/records")
    public List<RecRecordRepository.RecRecord> records(
            @PathVariable long runId,
            @RequestParam(defaultValue = "") String status) {
        String filter = status == null || status.isBlank() ? null : status;
        return service.records(runId, filter);
    }

    private RunApiModel api(RecRunRepository.RunView r) {
        return new RunApiModel(
                r.id(), r.datasetId(), r.domainId(), r.profileId(), r.domainRunId(),
                r.status(), r.startedAt(), r.completedAt(),
                r.sourceCount(), r.targetCount(), r.matched(),
                r.mismatched(), r.sourceOnly(), r.targetOnly(), r.errorMessage(),
                r.active(), r.reconMode(),
                r.sourceQuery(), r.targetQuery(), r.conditionFields()
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
