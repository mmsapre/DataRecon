package com.mms.data.recon.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(MmsRecon.PREFIX + ".build")
public class BuildInfoConfiguration {
    private String version = "dev";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
