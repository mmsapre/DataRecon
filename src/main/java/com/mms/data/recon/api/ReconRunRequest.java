package com.mms.data.recon.api;

import com.mms.data.recon.dataset.ReconMode;

import java.util.List;

public class ReconRunRequest {

    private ReconMode mode;
    private List<String> conditionFields;
    /** When true, force a FULL run even if a previous active run exists. Default is incremental. */
    private Boolean forceFull;

    public ReconMode getMode() { return mode; }
    public void setMode(ReconMode mode) { this.mode = mode; }

    public List<String> getConditionFields() { return conditionFields; }
    public void setConditionFields(List<String> conditionFields) { this.conditionFields = conditionFields; }

    public Boolean getForceFull() { return forceFull; }
    public void setForceFull(Boolean forceFull) { this.forceFull = forceFull; }
}
