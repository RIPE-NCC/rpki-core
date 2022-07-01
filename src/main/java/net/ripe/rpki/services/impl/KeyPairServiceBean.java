package net.ripe.rpki.services.impl;

import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import net.ripe.rpki.domain.DownStreamProvisioningCommunicator;
import net.ripe.rpki.domain.HardwareKeyPairFactory;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairEntityKeyInfo;
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
    public KeyPairEntity createKeyPairEntity(String name) {
        KeyPairEntityKeyInfo keyInfo = createKeyInfo(name);
        KeyPairEntitySignInfo signInfo = createSignInfo();
        String crlFilename = namingStrategy.crlFileName(keyInfo);
        String manifestFilename = namingStrategy.manifestFileName(keyInfo);
        return new KeyPairEntity(keyInfo, signInfo, crlFilename, manifestFilename);
    }

    @Override
    public DownStreamProvisioningCommunicator createMyIdentityMaterial(HostedCertificateAuthority ca) {
        KeyPairEntityKeyInfo keyInfo = createKeyInfo("");
        KeyPairEntitySignInfo signInfo = createSignInfo();
        return new DownStreamProvisioningCommunicator(keyInfo, signInfo, ca.getName());
    }

    private KeyPairEntitySignInfo createSignInfo() {
        return new KeyPairEntitySignInfo(
                providerConfiguration.getKeyStoreProvider(),
                providerConfiguration.getSignatureProvider(),
                providerConfiguration.getKeyStoreType());
    }

    private KeyPairEntityKeyInfo createKeyInfo(String name) {
        KeyPair keyPair = hardwareKeyPairFactory.get();
        return new KeyPairEntityKeyInfo(name, keyPair);
    }
}
