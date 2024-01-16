
package net.ripe.rpki.server.api.services.command;

/**
 * This exception indicates that duplicate resources are configured.
 */
public class IllegalResourceException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public IllegalResourceException(String message) {
        super(message);
    }
}
