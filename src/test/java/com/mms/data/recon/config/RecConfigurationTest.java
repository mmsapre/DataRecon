package com.mms.data.recon.config;

import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.HashingStrategy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecConfigurationTest {

    @Test
    void requireDomainThrowsForUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> new RecConfiguration().requireDomain("missing"));
    }

    @Test
    void requireProfileThrowsForUnknownId() {
        RecConfiguration configuration = configurationWithProfile("party", "pg-pg");
        assertThrows(IllegalArgumentException.class, () -> configuration.requireProfile("party", "missing"));
    }

    @Test
    void setDomainsAppliesDefaultsAndInitializes() {
        RecConfiguration configuration = configurationWithProfile("party", "pg-pg");

        DatasetConfiguration stored = configuration.requireProfile("party", "pg-pg");
        assertEquals("party.pg-pg", stored.getId());
        assertEquals("party", stored.getDomainId());
        assertEquals("pg-pg", stored.getProfileId());
        assertEquals(1000, stored.getBatchSize());
        assertEquals("landing", stored.getSource().getDatasourceRef());
        assertEquals(1, configuration.allProfiles().size());
    }

    @Test
    void putAndRemoveDomainAndProfileAtRuntime() {
        RecConfiguration configuration = new RecConfiguration();
        DomainConfiguration domain = new DomainConfiguration();
        domain.initialize("party", configuration.getDefaults(), false);
        configuration.putDomain("party", domain);
        assertEquals("party", configuration.requireDomain("party").getId());

        DatasetConfiguration profile = new DatasetConfiguration();
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("landing");
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("bq");
        profile.setSource(source);
        profile.setTarget(target);
        configuration.putProfile("party", "pg-bq", profile);

        assertEquals("party.pg-bq", configuration.requireProfile("party", "pg-bq").getId());
        configuration.removeProfile("party", "pg-bq");
        assertThrows(IllegalArgumentException.class, () -> configuration.requireProfile("party", "pg-bq"));
        configuration.removeDomain("party");
        assertThrows(IllegalArgumentException.class, () -> configuration.requireDomain("party"));
    }

    @Test
    void domainDefaultsOverrideGlobalDefaultsForProfiles() {
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("landing");
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("bq");
        DatasetConfiguration profile = new DatasetConfiguration();
        profile.setSource(source);
        profile.setTarget(target);

        DomainConfiguration domain = new DomainConfiguration();
        domain.setBatchSize(50);
        domain.setHashingStrategy(HashingStrategy.TypeStrict);
        Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
        profiles.put("pg-bq", profile);
        domain.setProfiles(profiles);

        RecConfiguration configuration = new RecConfiguration();
        Map<String, DomainConfiguration> domains = new LinkedHashMap<>();
        domains.put("party", domain);
        configuration.setDomains(domains);

        DatasetConfiguration stored = configuration.requireProfile("party", "pg-bq");
        assertEquals(50, stored.getBatchSize());
        assertEquals(HashingStrategy.TypeStrict, stored.getHashingStrategy());
    }

    private static RecConfiguration configurationWithProfile(String domainId, String profileId) {
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("landing");
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("bq");
        DatasetConfiguration profile = new DatasetConfiguration();
        profile.setSource(source);
        profile.setTarget(target);

        DomainConfiguration domain = new DomainConfiguration();
        Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
        profiles.put(profileId, profile);
        domain.setProfiles(profiles);

        RecConfiguration configuration = new RecConfiguration();
        Map<String, DomainConfiguration> domains = new LinkedHashMap<>();
        domains.put(domainId, domain);
        configuration.setDomains(domains);
        return configuration;
    }
}
