package net.ripe.rpki.server.api.services.command;

import javax.security.auth.x500.X500Principal;

/**
 * <p>
 * May be thrown when a {@link net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand} is used but
 * a CertificateAuthority of with the same name already exists in the system.
 * </p>
 */
public class CertificateAuthorityNameNotUniqueException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public CertificateAuthorityNameNotUniqueException(X500Principal memberCa) {
        super(String.format("CA for %s already exists.", memberCa.getName()));
    }
}
