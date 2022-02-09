package net.ripe.rpki.server.api.services.activation;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;

import javax.security.auth.x500.X500Principal;

/**
 * Creating CertificateAuthorities is ultimately done through normal Commands, but
 * first we check that the CA to make actually has certifiable resources, and this
 * needs admin privileges. Hence this separate service..
 */
public interface CertificateAuthorityCreateService {
    void createHostedCertificateAuthority(X500Principal name) throws CertificateAuthorityNameNotUniqueException;

    void createNonHostedCertificateAuthority(X500Principal name, ProvisioningIdentityCertificate identityCertificate) throws CertificateAuthorityNameNotUniqueException;

}
