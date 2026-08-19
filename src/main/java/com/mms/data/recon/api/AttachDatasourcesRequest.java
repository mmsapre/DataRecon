package com.mms.data.recon.api;

/**
 * Named catalog datasources to attach to a domain (defaults) or a profile's source/target.
 */
public class AttachDatasourcesRequest {

    private String source;
    private String target;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
}
