package net.ripe.rpki.core.services.background;

public class BackgroundServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BackgroundServiceException(String msg) {
        super(msg);
    }
}
