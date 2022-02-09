package net.ripe.rpki.domain;

/**
 * Creation of key pair related entities for use in resource certificates.
 */
public interface KeyPairService {

    KeyPairEntity createKeyPairEntity(String name);

    DownStreamProvisioningCommunicator createMyIdentityMaterial(HostedCertificateAuthority ca);
}
