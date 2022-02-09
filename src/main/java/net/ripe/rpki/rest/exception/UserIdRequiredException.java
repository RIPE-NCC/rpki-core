package net.ripe.rpki.rest.exception;


public class UserIdRequiredException extends RuntimeException {
    public UserIdRequiredException(String message) {
        super(message);
    }
}
