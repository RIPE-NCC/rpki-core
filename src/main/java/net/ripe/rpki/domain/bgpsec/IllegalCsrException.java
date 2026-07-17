
package net.ripe.rpki.domain.bgpsec;

import net.ripe.rpki.server.api.services.command.CertificationException;

/**
 * This exception indicates that the BGPSec certificate sign request is invalid.
 */
public class IllegalCsrException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public static final String INVALID_CSR_FOR_BGPSEC = "Invalid CSR for BGPSec: ";

    public IllegalCsrException(Throwable reason) {
        super(INVALID_CSR_FOR_BGPSEC + reason.getMessage());
    }

    public IllegalCsrException(String message) {
        super(INVALID_CSR_FOR_BGPSEC + message);
    }

    public IllegalCsrException(String message, Exception e) {
        super(INVALID_CSR_FOR_BGPSEC + message, e);
    }
}
