package com.mms.data.recon.dataset;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.recrun.RecRunService;

import java.util.concurrent.*;

@Singleton
public class DatasetRecScheduler implements ApplicationEventListener<ServerStartupEvent> {

    private final RecConfiguration configuration;
    private final RecRunService runService;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public DatasetRecScheduler(RecConfiguration configuration, RecRunService runService) {
        this.configuration = configuration;
        this.runService = runService;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // Schedule values like "60s", "5m", "1h". Domain schedule runs all profiles;
        // profile schedule runs that pairing only. Manual API trigger always works.
        configuration.getDomains().forEach((domainId, domain) -> {
            String domainSchedule = domain.getSchedule();
            if (domainSchedule != null && !domainSchedule.isBlank()) {
                long seconds = parseSeconds(domainSchedule);
                executor.scheduleWithFixedDelay(
                        () -> runService.runDomain(domainId).subscribe(),
                        seconds,
                        seconds,
                        TimeUnit.SECONDS
                );
            }
            domain.getProfiles().forEach((profileId, profile) -> {
                String profileSchedule = profile.getSchedule();
                if (profileSchedule == null || profileSchedule.isBlank()) {
                    return;
                }
                long seconds = parseSeconds(profileSchedule);
                executor.scheduleWithFixedDelay(
                        () -> runService.runProfile(domainId, profileId).subscribe(),
                        seconds,
                        seconds,
                        TimeUnit.SECONDS
                );
            });
        });
    }

    static long parseSeconds(String value) {
        String s = value.trim().toLowerCase();
        if (s.endsWith("ms")) return Math.max(1, Long.parseLong(s.substring(0, s.length() - 2)) / 1000);
        if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1));
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
        if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600;
        return Long.parseLong(s);
    }
}
