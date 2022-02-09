package net.ripe.rpki.server.api.services.command;

import net.ripe.ipresource.IpResourceSet;

/**
 * This exception indicates that resources were used that aren't held by the user.
 */
public class NotHolderOfResourcesException extends CertificationException {

    private static final long serialVersionUID = 1L;

    private IpResourceSet resources;

    public NotHolderOfResourcesException(IpResourceSet resources) {
        super("not holder of resources " + resources);
        this.resources = resources;
    }

    public IpResourceSet getResources() {
        return resources;
    }
}
