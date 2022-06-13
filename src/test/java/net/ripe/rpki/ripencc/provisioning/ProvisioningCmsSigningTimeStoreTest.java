package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class ProvisioningCmsSigningTimeStoreTest extends CertificationDomainTestCase {

    @Inject
    private ProvisioningCmsSigningTimeStore subject;

    private NonHostedCertificateAuthorityData nonHostedCa;

    @Before
    public void setUp() {
        clearDatabase();

        ProductionCertificateAuthority productionCa = createInitialisedProdCaWithRipeResources();
        NonHostedCertificateAuthority nonHostedCertificateAuthority = new NonHostedCertificateAuthority(
            123L,
            new X500Principal("CN=non-hosted"),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            productionCa
        );
        certificateAuthorityRepository.add(nonHostedCertificateAuthority);

        nonHostedCa = nonHostedCertificateAuthority.toData();
    }

    @Test
    public void should_track_last_seen_signing_time() {
        DateTime cmsSigningTime = DateTime.now(DateTimeZone.UTC);

        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa)).isEmpty();

        boolean updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime);
        assertThat(updated).isTrue();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .get().isEqualTo(cmsSigningTime);

        updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime.plusMinutes(1));
        assertThat(updated).isTrue();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .get().isEqualTo(cmsSigningTime.plusMinutes(1));

        updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime.minusMinutes(1));
        assertThat(updated).isFalse();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .get().isEqualTo(cmsSigningTime.plusMinutes(1));
    }
}
