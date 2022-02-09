package net.ripe.rpki.rest.exception;


public class CaNameInvalidException extends RuntimeException {
    public CaNameInvalidException(String rawCaName) {
        super("Invalid CA name: " + rawCaName);
    }
}