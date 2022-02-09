package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;

/**
 * An error response as described at the end of https://datatracker.ietf.org/doc/html/rfc6492#section-3.2 .
 * These will <emph>not</emph> result in CMS signed responses.
 *
 * Interpretation: Used for situations where the request was never considered to be a current, valid, cms-signed request.
 */
public class ProvisioningException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private ResponseExceptionType exceptionType;

    public ProvisioningException(ResponseExceptionType exceptionType, Throwable e) {
        super(exceptionType.toString(), e);
        this.exceptionType = exceptionType;
    }

    public ProvisioningException(ResponseExceptionType exceptionType) {
        super(exceptionType.toString());
        this.exceptionType = exceptionType;
    }

    public ResponseExceptionType getResponseExceptionType() {
        return exceptionType;
    }
}
