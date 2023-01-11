package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;


@Transactional
public class JpaRoaEntityRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaRoaEntityRepository subject;

    private KeyPairEntity keyPair;
    private ManagedCertificateAuthority certificateAuthority;

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
    public void findByCertificateSigningKeyPair() {
        assertThat(subject.findByCertificateSigningKeyPair(keyPair)).isEmpty();
    }

    @Test
    public void deleteByCertificateSigningKeyPair() {
        assertThat(subject.deleteByCertificateSigningKeyPair(keyPair)).isZero();
    }
}
