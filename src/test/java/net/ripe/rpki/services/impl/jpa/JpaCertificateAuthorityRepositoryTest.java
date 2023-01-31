package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class JpaCertificateAuthorityRepositoryTest extends CertificationDomainTestCase {

    @Before
    public void setUp() {
        clearDatabase();
    }

    @Test
    public void findAllWithOutdatedManifests() {
        assertThat(certificateAuthorityRepository.findAllWithOutdatedManifests(
            false,
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT),
            Integer.MAX_VALUE
        )).isEmpty();
        assertThat(certificateAuthorityRepository.findAllWithOutdatedManifests(
            true,
            UTC.dateTime().plus(ManifestEntity.TIME_TO_NEXT_UPDATE_SOFT_LIMIT),
            Integer.MAX_VALUE
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

    @Test
    public void getCasWithoutKeyPairsOlderThenOneYear() {
        assertThat(certificateAuthorityRepository.getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear()).isEmpty();
    }

    @Test
    public void should_throw_NameNotUniqueException_for_duplicate_ca_name() {
        ProductionCertificateAuthority prodCa = createInitialisedProdCaWithRipeResources();
        CertificateAuthority ca1 = new HostedCertificateAuthority(1000L, new X500Principal("CN=ca"), UUID.randomUUID(), prodCa);
        certificateAuthorityRepository.add(ca1);
        CertificateAuthority ca2 = new NonHostedCertificateAuthority(1001L, new X500Principal("CN=ca"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, prodCa);
        assertThatThrownBy(() -> certificateAuthorityRepository.add(ca2)).isInstanceOfSatisfying(
            NameNotUniqueException.class,
            (exception) -> assertThat(exception.getMessage()).isEqualTo("Name 'CN=ca' not unique.")
        );
    }
}
