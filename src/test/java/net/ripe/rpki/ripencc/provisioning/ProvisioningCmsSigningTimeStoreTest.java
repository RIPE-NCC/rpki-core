package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import org.junit.Before;
import org.junit.Test;

import jakarta.inject.Inject;
import javax.security.auth.x500.X500Principal;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        // Truncate to milliseconds due to the JVM dependant precision of Instant
        Instant cmsSigningTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa)).isEmpty();

        boolean updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime);
        assertThat(updated).isTrue();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .hasValue(cmsSigningTime);

        updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime.plus(1, ChronoUnit.MINUTES));
        assertThat(updated).isTrue();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .hasValue(cmsSigningTime.plus(1, ChronoUnit.MINUTES));

        updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, cmsSigningTime.minus(1, ChronoUnit.MINUTES));
        assertThat(updated).isFalse();
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa))
            .hasValue(cmsSigningTime.plus(1, ChronoUnit.MINUTES));
    }

    @Test
    public void should_track_last_seen_signing_time_jodatime() {
        Instant cmsSigningTime = Instant.now();

        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa)).isEmpty();
        boolean updated = subject.updateLastSeenProvisioningCmsSeenAt(nonHostedCa, org.joda.time.Instant.ofEpochMilli(cmsSigningTime.toEpochMilli()).toDateTime());
        assertThat(updated).isTrue();

        // Java instant has microsecond precision while the conversion has millisecond precision
        assertThat(subject.getLastSeenProvisioningCmsSignedAt(nonHostedCa).map(st -> st.truncatedTo(ChronoUnit.MILLIS)))
                .hasValue(cmsSigningTime.truncatedTo(ChronoUnit.MILLIS));
    }
}
