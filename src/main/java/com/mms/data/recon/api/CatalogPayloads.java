package com.mms.data.recon.api;

import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.ProfileDatasources;

/**
 * Maps in-memory catalog entities to upsert payloads for JSON persistence.
 */
final class CatalogPayloads {

    private CatalogPayloads() {}

    static DomainUpsertRequest fromDomain(DomainConfiguration domain) {
        DomainUpsertRequest request = new DomainUpsertRequest();
        request.setId(domain.getId());
        request.setSchedule(domain.getSchedule());
        request.setHashingStrategy(domain.getHashingStrategy());
        request.setBatchSize(domain.getBatchSize());
        request.setBatchConcurrency(domain.getBatchConcurrency());
        if (domain.getRecon() != null) {
            ReconRunRequest recon = new ReconRunRequest();
            recon.setMode(domain.getRecon().getMode());
            recon.setConditionFields(domain.getRecon().getConditionFields());
            request.setRecon(recon);
        }
        request.setTags(domain.getTags());
        return request;
    }

    static ProfileUpsertRequest fromProfile(DatasetConfiguration profile) {
        ProfileUpsertRequest request = new ProfileUpsertRequest();
        request.setId(profile.getProfileId());
        request.setSchedule(profile.getSchedule());
        request.setHashingStrategy(profile.getHashingStrategy());
        request.setBatchSize(profile.getBatchSize());
        request.setBatchConcurrency(profile.getBatchConcurrency());
        request.setMigrationKey(profile.getMigrationKey());
        if (profile.getRecon() != null) {
            ReconRunRequest recon = new ReconRunRequest();
            recon.setMode(profile.getRecon().getMode());
            recon.setConditionFields(profile.getRecon().getConditionFields());
            request.setRecon(recon);
        }
        if (profile.getDatasources() != null) {
            ProfileDatasources refs = new ProfileDatasources();
            refs.setSource(profile.getDatasources().getSource());
            refs.setTarget(profile.getDatasources().getTarget());
            request.setDatasources(refs);
        }
        request.setSource(fromSide(profile.getSource()));
        request.setTarget(fromSide(profile.getTarget()));
        request.setTags(profile.getTags());
        return request;
    }

    private static SideRequest fromSide(DataLoadDefinition side) {
        if (side == null) {
            return null;
        }
        SideRequest request = new SideRequest();
        request.setDatasource(side.getDatasourceRef());
        request.setType(side.getType());
        request.setSchema(side.getSchema());
        request.setTable(side.getTable());
        request.setCollection(side.getCollection());
        request.setFields(side.getFields());
        request.setQuery(side.getQuery());
        request.setQueryParams(side.getQueryParams());
        return request;
    }
}
