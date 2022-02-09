package net.ripe.rpki.server.api.services.command;

public abstract class CertificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public CertificationException(String message) {
        super(message);
    }

    public CertificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
