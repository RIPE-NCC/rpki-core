package net.ripe.rpki.domain;

/**
 * Creation of key pair related entities for use in resource certificates.
 */
public interface KeyPairService {

    KeyPairEntity createKeyPairEntity();

    KeyPairEntity createSpecialFsKeyPairEntity();

    DownStreamProvisioningCommunicator createMyIdentityMaterial(ManagedCertificateAuthority ca);
}
