package net.ripe.rpki.server.api.services.command;

public class CertificationResourceLimitExceededException extends CertificationException {
    public CertificationResourceLimitExceededException(String message) {
        super(message);
    }

    public CertificationResourceLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
