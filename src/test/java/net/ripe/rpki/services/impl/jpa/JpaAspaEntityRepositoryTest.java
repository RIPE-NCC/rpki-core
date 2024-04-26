package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;


@Transactional
public class JpaAspaEntityRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaAspaEntityRepository subject;

    private ManagedCertificateAuthority certificateAuthority;
    private KeyPairEntity keyPair;

    @Before
    public void setUp() {
        clearDatabase();
        certificateAuthority = createInitializedAllResourcesAndProductionCertificateAuthority();
        keyPair = certificateAuthority.getCurrentKeyPair();
    }

    @Test
    public void findCurrentByCertificateAuthority() {
        assertThat(subject.findCurrentByCertificateAuthority(certificateAuthority)).isEmpty();
    }

    @Test
    public void deleteByCertificateSigningKeyPair() {
        assertThat(subject.deleteByCertificateSigningKeyPair(keyPair)).isZero();
    }
}
