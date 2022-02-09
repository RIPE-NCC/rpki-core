package net.ripe.rpki.domain;

import net.ripe.rpki.server.api.services.command.CertificationException;

/**
 * Names must be unique (case insensitive).
 */
public class PublicKeyNotUniqueException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public PublicKeyNotUniqueException() {
        super("Public key not unique");
    }
}
