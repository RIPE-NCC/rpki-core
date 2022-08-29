package net.ripe.rpki.services.impl;

import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import net.ripe.rpki.domain.DownStreamProvisioningCommunicator;
import net.ripe.rpki.domain.HardwareKeyPairFactory;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairEntitySignInfo;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.KeyPair;

@Component
public class KeyPairServiceBean implements KeyPairService {

    private final CertificationProviderConfigurationData providerConfiguration;

    private final HardwareKeyPairFactory hardwareKeyPairFactory;
    private final UuidRepositoryObjectNamingStrategy namingStrategy;

    @Autowired
    public KeyPairServiceBean(
        CertificationProviderConfigurationData providerConfiguration,
        HardwareKeyPairFactory hardwareKeyPairFactory
    ) {
        this.hardwareKeyPairFactory = hardwareKeyPairFactory;
        this.providerConfiguration = providerConfiguration;
        this.namingStrategy = new UuidRepositoryObjectNamingStrategy();
    }

    @Override
    public KeyPairEntity createKeyPairEntity() {
        KeyPair keyPair = hardwareKeyPairFactory.get();
        KeyPairEntitySignInfo signInfo = createSignInfo();
        String crlFilename = namingStrategy.crlFileName(keyPair);
        String manifestFilename = namingStrategy.manifestFileName(keyPair);
        return new KeyPairEntity(keyPair, signInfo, crlFilename, manifestFilename);
    }

    @Override
    public DownStreamProvisioningCommunicator createMyIdentityMaterial(ManagedCertificateAuthority ca) {
        KeyPair keyPair = hardwareKeyPairFactory.get();
        KeyPairEntitySignInfo signInfo = createSignInfo();
        return new DownStreamProvisioningCommunicator(keyPair, signInfo, ca.getName());
    }

    private KeyPairEntitySignInfo createSignInfo() {
        return new KeyPairEntitySignInfo(
                providerConfiguration.getKeyStoreProvider(),
                providerConfiguration.getSignatureProvider(),
                providerConfiguration.getKeyStoreType());
    }

}
