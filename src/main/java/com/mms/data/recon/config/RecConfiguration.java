package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.ReconMode;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(MmsRecon.PREFIX)
public class RecConfiguration {

    /** Stamped on created_by / updated_by when the application writes rows. */
    private String actor = "data-recon";
    private Defaults defaults = new Defaults();
    private Map<String, DomainConfiguration> domains = new LinkedHashMap<>();

    public String getActor() {
        return actor == null || actor.isBlank() ? "data-recon" : actor.trim();
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Defaults getDefaults() { return defaults; }
    public void setDefaults(Defaults defaults) { this.defaults = defaults; }

    public Map<String, DomainConfiguration> getDomains() { return domains; }

    public void setDomains(Map<String, DomainConfiguration> domains) {
        this.domains = domains == null ? new LinkedHashMap<>() : domains;
        this.domains.forEach((id, domain) -> domain.initialize(id, defaults));
    }

    public DomainConfiguration requireDomain(String domainId) {
        DomainConfiguration domain = domains.get(domainId);
        if (domain == null) {
            throw new IllegalArgumentException("Unknown domain: " + domainId);
        }
        return domain;
    }

    public synchronized DomainConfiguration putDomain(String domainId, DomainConfiguration domain) {
        String id = DomainConfiguration.requireName("domain", domainId);
        domain.setId(id);
        if (domain.getProfiles() == null) {
            domain.setProfiles(new LinkedHashMap<>());
        }
        domains.put(id, domain);
        return domain;
    }

    public synchronized DomainConfiguration removeDomain(String domainId) {
        DomainConfiguration removed = domains.remove(requireDomain(domainId).getId());
        if (removed == null) {
            throw new IllegalArgumentException("Unknown domain: " + domainId);
        }
        return removed;
    }

    public synchronized DatasetConfiguration putProfile(
            String domainId,
            String profileId,
            DatasetConfiguration profile) {
        DomainConfiguration domain = requireDomain(domainId);
        DatasetConfiguration stored = domain.initializeProfile(profileId, profile, defaults);
        domain.getProfiles().put(stored.getProfileId(), stored);
        return stored;
    }

    public synchronized DatasetConfiguration removeProfile(String domainId, String profileId) {
        DomainConfiguration domain = requireDomain(domainId);
        DatasetConfiguration removed = domain.getProfiles().remove(profileId);
        if (removed == null) {
            throw new IllegalArgumentException("Unknown profile: " + domainId + "." + profileId);
        }
        return removed;
    }

    public DatasetConfiguration requireProfile(String domainId, String profileId) {
        return requireDomain(domainId).requireProfile(profileId);
    }

    public Collection<DatasetConfiguration> allProfiles() {
        return domains.values().stream()
                .flatMap(domain -> domain.getProfiles().values().stream())
                .toList();
    }

    public static class Defaults {
        private int batchSize = 1000;
        private int batchConcurrency = 5;
        private HashingStrategy hashingStrategy = HashingStrategy.TypeLenient;
        private Path queryFileBaseDir = Path.of("queries");
        private ReconMode reconMode = ReconMode.MISMATCH_DETAILS;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getBatchConcurrency() { return batchConcurrency; }
        public void setBatchConcurrency(int batchConcurrency) { this.batchConcurrency = batchConcurrency; }

        public HashingStrategy getHashingStrategy() { return hashingStrategy; }
        public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

        public Path getQueryFileBaseDir() { return queryFileBaseDir; }
        public void setQueryFileBaseDir(Path queryFileBaseDir) { this.queryFileBaseDir = queryFileBaseDir; }

        public ReconMode getReconMode() { return reconMode; }
        public void setReconMode(ReconMode reconMode) { this.reconMode = reconMode; }
    }
}
