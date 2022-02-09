package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;

import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NonHostedCertificateAuthorityTest {

    private PregeneratedKeyPairFactory keyPairFactory = PregeneratedKeyPairFactory.getInstance();

    @Test
    void findOrCreatePublicKeyEntityByPublicKey_should_limit_number_of_public_keys() {
        NonHostedCertificateAuthority subject = new NonHostedCertificateAuthority(12, new X500Principal("CN=test"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, null);
        for (int i = 0; i < NonHostedCertificateAuthority.PUBLIC_KEY_LIMIT - 1; ++i) {
            PublicKey publicKey = keyPairFactory.generate().getPublic();
            subject.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        }

        PublicKeyEntity lastAdded = subject.findOrCreatePublicKeyEntityByPublicKey(keyPairFactory.generate().getPublic());

        assertThat(subject.findOrCreatePublicKeyEntityByPublicKey(lastAdded.getPublicKey()))
            .isSameAs(lastAdded);
        assertThatThrownBy(() -> subject.findOrCreatePublicKeyEntityByPublicKey(keyPairFactory.generate().getPublic()))
            .isInstanceOf(CertificationResourceLimitExceededException.class);
    }
}
