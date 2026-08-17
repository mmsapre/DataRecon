package com.mms.data.recon.llm;

import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.dataset.InMemoryRecStores;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmSummaryServiceTest {

    @Test
    void promptIncludesCountsAndMismatchKeysNotBusinessValues() {
        RecRunRepository.RunView run = new RecRunRepository.RunView(
                9L, "party.pg-pg", "party", "pg-pg", null,
                "COMPLETED", Instant.now(), Instant.now(),
                10, 10, 8, 1, 1, 0, null, true, "MISMATCH_DETAILS",
                "SELECT 1", "SELECT 1", List.of("status")
        );
        List<RecRecordRepository.RecRecord> records = List.of(
                new RecRecordRepository.RecRecord("P1", "h1", "h2", RecRecordRepository.RecStatus.MISMATCHED, "{\"status\":\"MISMATCHED\"}"),
                new RecRecordRepository.RecRecord("P2", "h", "h", RecRecordRepository.RecStatus.MATCHED, null)
        );

        String prompt = LlmSummaryService.promptForRun(run, records, 50);
        assertTrue(prompt.contains("mismatched=1"));
        assertTrue(prompt.contains("key=P1"));
        assertTrue(prompt.contains("fieldDiffs={\"status\":\"MISMATCHED\"}"));
        assertTrue(!prompt.contains("key=P2"));
        assertTrue(LlmSummaryService.systemPrompt().contains("Data Recon"));
    }

    @Test
    void resolveRequiresUrlAndApiKey() {
        LlmSummaryService service = new LlmSummaryService(
                new LlmProperties(),
                new OpenAiCompatibleLlmClient(),
                new InMemoryRecStores.MemoryRecRunRepository(),
                new InMemoryRecStores.MemoryRecRecordRepository()
        );
        assertThrows(IllegalStateException.class, () -> service.resolve(null));

        LlmProperties request = new LlmProperties();
        request.setUrl("https://api.openai.com/v1");
        request.setApiKey("sk-test");
        LlmProperties resolved = service.resolve(request);
        assertTrue(resolved.configured());
        assertEquals("sk-test", resolved.getApiKey());
    }

    @Test
    void extractsChatCompletionContent() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient();
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"2 mismatches on party."}}]}
                """;
        assertEquals("2 mismatches on party.", client.extractContent(json));
    }
}
