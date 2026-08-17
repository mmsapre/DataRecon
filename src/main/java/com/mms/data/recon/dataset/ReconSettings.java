package com.mms.data.recon.dataset;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile (or domain) recon output: counts-only, mismatch details, or field-level mismatch details.
 */
public class ReconSettings {

    private ReconMode mode;
    private List<String> conditionFields = new ArrayList<>();

    public ReconSettings copy() {
        ReconSettings copy = new ReconSettings();
        copy.setMode(mode);
        copy.setConditionFields(new ArrayList<>(conditionFields == null ? List.of() : conditionFields));
        return copy;
    }

    public ReconSettings overlay(ReconMode overrideMode, List<String> overrideFields) {
        ReconSettings copy = copy();
        if (overrideMode != null) {
            copy.setMode(overrideMode);
        }
        if (overrideFields != null) {
            copy.setConditionFields(new ArrayList<>(overrideFields));
        }
        copy.normalize();
        return copy;
    }

    public void apply(ReconMode newMode, List<String> newFields) {
        if (newMode != null) {
            this.mode = newMode;
        }
        if (newFields != null) {
            this.conditionFields = new ArrayList<>(newFields);
        }
        normalize();
    }

    public void normalize() {
        if (mode == null) {
            mode = ReconMode.MISMATCH_DETAILS;
        }
        if (conditionFields == null) {
            conditionFields = new ArrayList<>();
        }
    }

    public ReconMode resolvedMode() {
        return mode == null ? ReconMode.MISMATCH_DETAILS : mode;
    }

    public List<String> resolvedConditionFields() {
        return conditionFields == null ? List.of() : List.copyOf(conditionFields);
    }

    public ReconMode getMode() { return mode; }
    public void setMode(ReconMode mode) { this.mode = mode; }

    public List<String> getConditionFields() { return conditionFields; }
    public void setConditionFields(List<String> conditionFields) {
        this.conditionFields = conditionFields == null ? new ArrayList<>() : new ArrayList<>(conditionFields);
    }
}
