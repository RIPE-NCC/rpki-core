package net.ripe.rpki.server.api.configuration;


public class CertificationConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CertificationConfigurationException(String message) {
        super(message);
    }

    public CertificationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

}
