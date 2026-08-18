package com.mms.data.recon.config;

/**
 * Thrown when recon configuration is invalid or incomplete.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
