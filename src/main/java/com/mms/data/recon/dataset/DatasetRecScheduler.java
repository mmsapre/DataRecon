package com.mms.data.recon.dataset;

import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.recrun.RecRunService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class DatasetRecScheduler {

    private final RecConfiguration configuration;
    private final RecRunService runService;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public DatasetRecScheduler(RecConfiguration configuration, RecRunService runService) {
        this.configuration = configuration;
        this.runService = runService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Schedule values like "60s", "5m", "1h". Domain schedule runs all profiles;
        // profile schedule runs that pairing only. Manual API trigger always works.
        configuration.getDomains().values().forEach(this::syncDomain);
    }

    public synchronized void syncDomain(DomainConfiguration domain) {
        cancelDomain(domain.getId());
        String domainId = domain.getId();
        schedule(domainKey(domainId), domain.getSchedule(), () -> runService.runDomain(domainId).subscribe());
        domain.getProfiles().forEach((profileId, profile) ->
                schedule(
                        profileKey(domainId, profileId),
                        profile.getSchedule(),
                        () -> runService.runProfile(domainId, profileId).subscribe()
                ));
    }

    public synchronized void cancelDomain(String domainId) {
        cancel(domainKey(domainId));
        String prefix = profilePrefix(domainId);
        List<String> keys = new ArrayList<>();
        for (String key : jobs.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.forEach(this::cancel);
    }

    private void schedule(String key, String schedule, Runnable task) {
        if (schedule == null || schedule.isBlank()) {
            return;
        }
        long seconds = parseSeconds(schedule);
        jobs.put(key, executor.scheduleWithFixedDelay(task, seconds, seconds, TimeUnit.SECONDS));
    }

    private void cancel(String key) {
        ScheduledFuture<?> existing = jobs.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private static String domainKey(String domainId) {
        return "domain:" + domainId;
    }

    private static String profilePrefix(String domainId) {
        return "profile:" + domainId + ".";
    }

    private static String profileKey(String domainId, String profileId) {
        return profilePrefix(domainId) + profileId;
    }

    public static long parseSeconds(String value) {
        String s = value.trim().toLowerCase();
        if (s.endsWith("ms")) return Math.max(1, Long.parseLong(s.substring(0, s.length() - 2)) / 1000);
        if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1));
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
        if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600;
        return Long.parseLong(s);
    }
}
