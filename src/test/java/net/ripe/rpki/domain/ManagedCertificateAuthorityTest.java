package net.ripe.rpki.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.domain.CertificationDomainTestCase.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ManagedCertificateAuthorityTest {

    private static final ValidityPeriod VALIDITY_PERIOD = new ValidityPeriod(new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC).plusDays(1));

    private CertificateManagementService certificateManagementService;

    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    @Mock
    private PublishedObjectRepository publishedObjectRepository;

    @Mock
    private CrlEntityRepository crlEntityRepository;

    @Mock
    private ManifestEntityRepository manifestEntityRepository;

    private ManagedCertificateAuthority subject;
    private KeyPairEntity keyPair;
    private PublishedObject toBePublished;
    private PublishedObject toBeWithdrawn;

    @Before
    public void setUp() {
        certificateManagementService = new CertificateManagementServiceImpl(resourceCertificateRepository, publishedObjectRepository, crlEntityRepository, manifestEntityRepository,
            new SingleUseKeyPairFactory(PregeneratedKeyPairFactory.getInstance()), new SimpleMeterRegistry());
        subject = createInitialisedProdCaWithRipeResources(certificateManagementService);
        keyPair = subject.getCurrentKeyPair();
        toBePublished = new PublishedObject(keyPair, "object.cer", new byte[]{1, 2, 3}, true, BASE_URI, VALIDITY_PERIOD);
        toBeWithdrawn = new PublishedObject(keyPair, "withdrawn.cer", new byte[]{2, 3, 4}, true, BASE_URI, VALIDITY_PERIOD);
        toBeWithdrawn.published();
        toBeWithdrawn.withdraw();
    }

    @Test
    public void shouldPublishForCurrentKeyPair() {
        CrlEntity crlEntity = new CrlEntity(keyPair);
        when(crlEntityRepository.findOrCreateByKeyPair(keyPair)).thenReturn(crlEntity);

        ManifestEntity manifestEntity = new ManifestEntity(keyPair);
        when(manifestEntityRepository.findOrCreateByKeyPairEntity(keyPair)).thenReturn(manifestEntity);

        when(publishedObjectRepository.findActiveManifestEntries(keyPair)).thenReturn(Collections.singletonList(toBePublished));

        certificateManagementService.updateManifestAndCrlIfNeeded(subject);

        verify(crlEntityRepository).add(same(crlEntity));
        X509Crl crl = crlEntity.getCrl();

        // Manifest should contain the CRL and self signed certificate
        verify(manifestEntityRepository).add(same(manifestEntity));
        ManifestCms manifestCms = manifestEntity.getManifestCms();
        assertThat(manifestCms.getFiles()).hasSize(1);
        assertThat(manifestCms.getFiles().get("object.cer")).isNotNull();

        assertThat(manifestCms.getCertificate().getValidityPeriod().getNotValidAfter())
            .isEqualTo(manifestCms.getNextUpdateTime())
            .isEqualTo(crl.getNextUpdateTime());
    }

    @Test
    public void should_not_create_revoke_request_for_current_key() {
        assertThat(keyPair.getStatus()).isEqualTo(KeyPairStatus.CURRENT);
        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).isEmpty();
    }

    @Test
    public void should_create_revoke_request_for_old_keys_without_current_outgoing_resource_certificates() {
        keyPair.deactivate();
        when(resourceCertificateRepository.existsCurrentOutgoingCertificatesExceptForManifest(keyPair)).thenReturn(false);

        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).hasSize(1);
    }

    @Test
    public void should_not_create_revoke_request_for_old_keys_with_current_outgoing_resource_certificates() {
        keyPair.deactivate();
        when(resourceCertificateRepository.existsCurrentOutgoingCertificatesExceptForManifest(keyPair)).thenReturn(true);

        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).isEmpty();
    }

}
