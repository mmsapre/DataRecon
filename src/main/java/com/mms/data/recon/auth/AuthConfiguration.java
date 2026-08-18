package com.mms.data.recon.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.mms.data.recon.config.MmsRecon;

@ConfigurationProperties(MmsRecon.PREFIX + ".auth")
public class AuthConfiguration {
    private String username = "";
    private String password = "";

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean enabled() {
        return username != null && !username.isBlank();
    }
}

