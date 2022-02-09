package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class JpaCertificateAuthorityRepositoryTest extends CertificationDomainTestCase {

    @Before
    public void setUp() {
        clearDatabase();
    }

    @Test
    public void findAllWithPendingPublications() {
        assertThat(certificateAuthorityRepository.findAllWithPendingPublications(LockModeType.PESSIMISTIC_WRITE)).isEmpty();
    }

    @Test
    public void deleteNonHostedPublicKeysWithoutSigningCertificates() {
        assertThat(certificateAuthorityRepository.deleteNonHostedPublicKeysWithoutSigningCertificates()).isZero();
    }
}
