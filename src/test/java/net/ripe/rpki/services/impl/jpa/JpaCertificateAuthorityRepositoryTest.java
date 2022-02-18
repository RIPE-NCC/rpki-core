package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import org.junit.Before;
import org.junit.Test;
import net.ripe.rpki.domain.manifest.ManifestEntity;

import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class JpaCertificateAuthorityRepositoryTest extends CertificationDomainTestCase {

    @Before
    public void setUp() {
        clearDatabase();
    }

    @Test
    public void findAllWithOutdatedManifests() {
        assertThat(certificateAuthorityRepository.findAllWithOutdatedManifests(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT)
        )).isEmpty();
    }

    @Test
    public void findAllWithPendingPublications() {
        assertThat(certificateAuthorityRepository.findAllWithManifestsExpiringBefore(
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT),
            100
        )).isEmpty();
    }

    @Test
    public void deleteNonHostedPublicKeysWithoutSigningCertificates() {
        assertThat(certificateAuthorityRepository.deleteNonHostedPublicKeysWithoutSigningCertificates()).isZero();
    }
}
