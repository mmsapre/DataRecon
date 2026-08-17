package com.mms.data.recon.llm;

import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class LlmSummaryService {

    private final LlmProperties properties;
    private final OpenAiCompatibleLlmClient client;
    private final RecRunRepository runRepository;
    private final RecRecordRepository recordRepository;

    public LlmSummaryService(
            LlmProperties properties,
            OpenAiCompatibleLlmClient client,
            RecRunRepository runRepository,
            RecRecordRepository recordRepository) {
        this.properties = properties;
        this.client = client;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
    }

    public Summary summarizeRun(long runId, LlmProperties override) {
        RecRunRepository.RunView run = runRepository.find(runId);
        if (run == null) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        LlmProperties settings = resolve(override);
        List<RecRecordRepository.RecRecord> records = recordRepository.findByRun(runId, null);
        String prompt = promptForRun(run, records, settings.getMaxRecords());
        String text = client.complete(settings, systemPrompt(), prompt);
        return new Summary(runId, settings.getModel(), text);
    }

    public Summary summarizeDomainRun(long domainRunId, LlmProperties override) {
        RecRunRepository.RunView parent = runRepository.find(domainRunId);
        if (parent == null || parent.profileId() != null) {
            throw new IllegalArgumentException("Unknown domain run: " + domainRunId);
        }
        LlmProperties settings = resolve(override);
        List<RecRunRepository.RunView> profiles = runRepository.listByDomainRun(domainRunId).stream()
                .filter(run -> run.profileId() != null)
                .toList();
        String prompt = promptForDomain(parent, profiles);
        String text = client.complete(settings, systemPrompt(), prompt);
        return new Summary(domainRunId, settings.getModel(), text);
    }

    LlmProperties resolve(LlmProperties override) {
        if (override != null && override.configured()) {
            override.setTimeoutSeconds(properties.getTimeoutSeconds());
            override.setMaxRecords(properties.getMaxRecords());
            return override;
        }
        if (override != null) {
            LlmProperties merged = properties.overlay(override.getUrl(), override.getApiKey(), override.getModel());
            if (merged.configured()) {
                return merged;
            }
        }
        if (properties.configured()) {
            return properties;
        }
        throw new IllegalStateException(
                "LLM is not configured: set mms.recon.llm.url and mms.recon.llm.api-key, or pass url and apiKey on the request"
        );
    }

    static String systemPrompt() {
        return "You are Data Recon. Summarize reconciliation run results for operators. "
                + "Use only the counts, statuses, and mismatch keys provided. "
                + "Do not invent business field values. Be concise: overall outcome, notable gaps, and what to check next.";
    }

    static String promptForRun(
            RecRunRepository.RunView run,
            List<RecRecordRepository.RecRecord> records,
            int maxRecords) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize this Data Recon profile run.\n");
        prompt.append("domain=").append(run.domainId())
                .append(" profile=").append(run.profileId())
                .append(" runId=").append(run.id())
                .append(" status=").append(run.status())
                .append(" mode=").append(run.reconMode())
                .append(" active=").append(run.active()).append('\n');
        prompt.append("sourceCount=").append(run.sourceCount())
                .append(" targetCount=").append(run.targetCount())
                .append(" matched=").append(run.matched())
                .append(" mismatched=").append(run.mismatched())
                .append(" sourceOnly=").append(run.sourceOnly())
                .append(" targetOnly=").append(run.targetOnly()).append('\n');
        if (run.errorMessage() != null && !run.errorMessage().isBlank()) {
            prompt.append("error=").append(run.errorMessage()).append('\n');
        }
        List<RecRecordRepository.RecRecord> sample = records.stream()
                .filter(record -> record.status() != RecRecordRepository.RecStatus.MATCHED)
                .limit(Math.max(1, maxRecords))
                .toList();
        prompt.append("detailRows=").append(records.size())
                .append(" sampledNonMatches=").append(sample.size()).append('\n');
        for (RecRecordRepository.RecRecord record : sample) {
            prompt.append("- key=").append(record.migrationKey())
                    .append(" status=").append(record.status());
            if (record.fieldDiffs() != null && !record.fieldDiffs().isBlank()) {
                prompt.append(" fieldDiffs=").append(record.fieldDiffs());
            }
            prompt.append('\n');
        }
        return prompt.toString();
    }

    static String promptForDomain(RecRunRepository.RunView parent, List<RecRunRepository.RunView> profiles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize this Data Recon domain run across profiles.\n");
        prompt.append("domain=").append(parent.domainId())
                .append(" domainRunId=").append(parent.id())
                .append(" status=").append(parent.status())
                .append(" matched=").append(parent.matched())
                .append(" mismatched=").append(parent.mismatched())
                .append(" sourceOnly=").append(parent.sourceOnly())
                .append(" targetOnly=").append(parent.targetOnly()).append('\n');
        prompt.append(profiles.stream()
                .map(run -> "profile=" + run.profileId()
                        + " runId=" + run.id()
                        + " status=" + run.status()
                        + " matched=" + run.matched()
                        + " mismatched=" + run.mismatched()
                        + " sourceOnly=" + run.sourceOnly()
                        + " targetOnly=" + run.targetOnly())
                .collect(Collectors.joining("\n")));
        return prompt.toString();
    }

    public record Summary(long runId, String model, String text) {}
}
