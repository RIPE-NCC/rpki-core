package net.ripe.rpki.rest.exception;


public class CaNotFoundException extends RuntimeException {
    public CaNotFoundException(String message) {
        super(message);
    }
}
