package net.ripe.rpki.domain;

import net.ripe.rpki.server.api.services.command.CertificationException;

import javax.security.auth.x500.X500Principal;

/**
 * Names must be unique (case insensitive).
 */
public class NameNotUniqueException extends CertificationException {

    private static final long serialVersionUID = 1L;

    private final String name;


    public NameNotUniqueException(String name) {
        super("Name '" + name + "' not unique.");
        this.name = name;
    }

    public NameNotUniqueException(X500Principal name) {
        this(name.getName());
    }

    public String getName() {
        return name;
    }
}
