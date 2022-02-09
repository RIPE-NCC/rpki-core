package net.ripe.rpki.domain;


public class CertificateAuthorityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CertificateAuthorityException(String message) {
        super(message);
    }

    public CertificateAuthorityException(String message, Throwable cause) {
        super(message, cause);
    }
}
