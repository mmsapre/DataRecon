package com.mms.data.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(MmsRecon.PREFIX + ".build")
public class BuildInfoConfiguration {
    private String version = "dev";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
