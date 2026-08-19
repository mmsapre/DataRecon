package com.mms.data.recon.api;

import java.util.List;

/**
 * Agent-friendly profile trigger: resolve by profile id, qualified id ({@code domain.profile}),
 * or profile name with optional domain.
 */
public class ProfileTriggerRequest {

    /** Profile id, qualified id ({@code party.pg-mongo}), or profile name. */
    private String profile;
    /** Optional domain when {@link #profile} is only the profile id and may not be unique. */
    private String domain;
    private List<String> conditionFields;
    private Boolean forceFull;

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public List<String> getConditionFields() { return conditionFields; }
    public void setConditionFields(List<String> conditionFields) { this.conditionFields = conditionFields; }

    public Boolean getForceFull() { return forceFull; }
    public void setForceFull(Boolean forceFull) { this.forceFull = forceFull; }
}
