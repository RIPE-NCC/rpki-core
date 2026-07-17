package net.ripe.rpki.rest.exception;

public class BadRouterIdException extends IllegalArgumentException {
    public BadRouterIdException(String message) {
        super(message);
    }
}
