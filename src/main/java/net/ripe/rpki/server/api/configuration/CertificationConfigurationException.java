package net.ripe.rpki.server.api.configuration;


public class CertificationConfigurationException extends RuntimeException {

    public CertificationConfigurationException(String message) {
        super(message);
    }

    public CertificationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

}
