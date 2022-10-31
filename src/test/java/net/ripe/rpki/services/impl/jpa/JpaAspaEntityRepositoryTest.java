package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.TestObjects;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;


@Transactional
public class JpaAspaEntityRepositoryTest extends CertificationDomainTestCase {

    @Inject
    private JpaAspaEntityRepository subject;

    private KeyPairEntity keyPair;

    @Before
    public void setUp() {
        keyPair = TestObjects.createTestKeyPair();
        entityManager.persist(keyPair);
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
