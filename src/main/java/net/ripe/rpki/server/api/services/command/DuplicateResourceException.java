
package net.ripe.rpki.server.api.services.command;

/**
 * This exception indicates that duplicate resources are configured.
 */
public class DuplicateResourceException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(message);
    }
}
