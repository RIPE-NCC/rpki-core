package net.ripe.rpki.server.api.services.command;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;

/**
 * This exception indicates that resources were used that aren't held by the user.
 */
public class NotHolderOfResourcesException extends CertificationException {

    private static final long serialVersionUID = 1L;

    @Getter
    private final ImmutableResourceSet resources;

    public NotHolderOfResourcesException(ImmutableResourceSet resources) {
        super("not holder of resources " + resources);
        this.resources = resources;
    }
}
