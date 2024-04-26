package net.ripe.rpki.offline.ra.service;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.UnknownCertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.request.*;
import net.ripe.rpki.commons.ta.domain.response.ErrorResponse;
import net.ripe.rpki.commons.ta.domain.response.RevocationResponse;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.OfflineResponseProcessorException;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import jakarta.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.*;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest.createSelfSignedCaResourceCertificate;
import static net.ripe.rpki.domain.TestObjects.ACA_ID;
import static net.ripe.rpki.domain.TestObjects.ALL_RESOURCES_CA_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class TrustAnchorResponseProcessorTest {

    @Rule
    public FixedDateRule fixedDateRule = new FixedDateRule(1);


    private static final X500Principal CA_NAME = new X500Principal("CN=test");
    private static final X509ResourceCertificate NEW_CERTIFICATE = createSelfSignedCaResourceCertificate(new IpResourceSet(ALL_PRIVATE_USE_RESOURCES));
    private static final String NEW_CERTIFICATE_FILE_NAME = "cert.cer";
    private static final URI NEW_CERTIFICATE_PUBLICATION_BASE_URI = URI.create("rsync://nowhere/");
    private static final URI NEW_CERTIFICATE_PUBLICATION_URI = NEW_CERTIFICATE_PUBLICATION_BASE_URI.resolve(NEW_CERTIFICATE_FILE_NAME);

    private static final SigningResponse TEST_SIGN_RESPONSE = new SigningResponse(UUID.randomUUID(), "RIPE", NEW_CERTIFICATE_PUBLICATION_URI, NEW_CERTIFICATE);
    private static final Map<URI, CertificateRepositoryObject> TA_OBJECTS = new HashMap<>();
    static {
        TA_OBJECTS.put(NEW_CERTIFICATE_PUBLICATION_URI, NEW_CERTIFICATE);
    }

    private TrustAnchorResponseProcessor subject;

    @Mock
    private TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private KeyPairDeletionService keyPairDeletionService;
    @Mock
    private ResourceCache resourceCache;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private AllResourcesCertificateAuthority allResourcesCA;

    @Before
    public void setUp() {
        subject = new TrustAnchorResponseProcessor(CA_NAME, certificateAuthorityRepository,
                trustAnchorPublishedObjectRepository, keyPairDeletionService, resourceCache);
        subject.setEntityManager(entityManager);
        allResourcesCA = new AllResourcesCertificateAuthority(ACA_ID, ALL_RESOURCES_CA_NAME, UUID.randomUUID());
    }

    @Test
    public void should_process_a_sign_response_and_remove_pending_request() {
        AllResourcesCertificateAuthority allResourcesCertificateAuthority = mock(AllResourcesCertificateAuthority.class);
        when(allResourcesCertificateAuthority.getName()).thenReturn(new X500Principal("CN=ACA"));

        UpStreamCARequestEntity pendingRequest = createUpStreamCARequestEntity(allResourcesCertificateAuthority);
        given(allResourcesCertificateAuthority.getUpStreamCARequestEntity()).willReturn(pendingRequest);
        given(certificateAuthorityRepository.findAllResourcesCAByName(CA_NAME)).willReturn(allResourcesCertificateAuthority);

        subject.process(getResponseWithSignedCertificates(1L, TA_OBJECTS, TEST_SIGN_RESPONSE));

        // expect that the pending request is revoked
        verify(allResourcesCertificateAuthority).setUpStreamCARequestEntity(null);
        verify(entityManager).remove(isA(UpStreamCARequestEntity.class));
        verify(entityManager, atLeastOnce()).flush();

        verify(allResourcesCertificateAuthority).processCertificateIssuanceResponse(isA(CertificateIssuanceResponse.class), isNull());
    }

    @Test
    public void should_process_response_and_re_publish_object() {
        var now = Instant.now();
        final byte[] content = {'a'};
        final TrustAnchorPublishedObject existingObject = new TrustAnchorPublishedObject(NEW_CERTIFICATE_PUBLICATION_URI, content, now);

        given(trustAnchorPublishedObjectRepository.findActiveObjects())
                .willReturn(Collections.singletonList(existingObject));

        final List<TrustAnchorPublishedObject> publishedObjects = subject.applyChangeToPublishedObjects(TA_OBJECTS);

        assertThat(publishedObjects.size()).isEqualTo(2);

        final TrustAnchorPublishedObject actual0 = publishedObjects.get(0);
        assertThat(actual0.getUri()).isEqualTo(NEW_CERTIFICATE_PUBLICATION_URI);
        assertThat(actual0.getContent()).isEqualTo(content);
        assertThat(actual0.getStatus().isPublished()).isFalse();
        assertThat(actual0.getCreatedAt()).isEqualTo(now);

        final TrustAnchorPublishedObject actual1 = publishedObjects.get(1);

        assertThat(actual1.getUri()).isEqualTo(NEW_CERTIFICATE_PUBLICATION_URI);
        assertThat(actual1.getContent()).isEqualTo(NEW_CERTIFICATE.getEncoded());
        assertThat(actual1.getStatus()).isEqualTo(PublicationStatus.TO_BE_PUBLISHED);
        assertThat(actual1.getCreatedAt()).isEqualTo(NEW_CERTIFICATE.getValidityPeriod().getNotValidBefore().toInstant());
    }

    @Test
    public void should_process_response_for_unknown_file_and_use_now_as_default_time() {
        var now = Instant.now();
        var secondObjectUri = NEW_CERTIFICATE_PUBLICATION_BASE_URI.resolve("secondObject.bin");

        var toPublishObjects = Map.of(
            NEW_CERTIFICATE_PUBLICATION_URI, NEW_CERTIFICATE,
            secondObjectUri, new UnknownCertificateRepositoryObject(new byte[]{'d', 'e', 'a', 'd', 'b', 'e', 'e', 'f'})
        );

        final List<TrustAnchorPublishedObject> publishedObjects = subject.applyChangeToPublishedObjects(toPublishObjects);

        assertThat(publishedObjects.size()).isEqualTo(2);

        final TrustAnchorPublishedObject actual0 = publishedObjects.stream().filter(obj -> NEW_CERTIFICATE_PUBLICATION_URI.equals(obj.getUri())).findFirst().get();
        assertThat(actual0.getUri()).isEqualTo(NEW_CERTIFICATE_PUBLICATION_URI);
        assertThat(actual0.getContent()).isEqualTo(NEW_CERTIFICATE.getEncoded());
        assertThat(actual0.getStatus()).isEqualTo(PublicationStatus.TO_BE_PUBLISHED);
        assertThat(actual0.getCreatedAt()).isEqualTo(NEW_CERTIFICATE.getValidityPeriod().getNotValidBefore().toInstant());

        final TrustAnchorPublishedObject actual1 = publishedObjects.stream().filter(obj -> secondObjectUri.equals(obj.getUri())).findFirst().get();
        assertThat(actual1.getUri()).isEqualTo(secondObjectUri);
        assertThat(actual1.getStatus()).isEqualTo(PublicationStatus.TO_BE_PUBLISHED);
        assertThat(actual1.getCreatedAt()).isGreaterThanOrEqualTo(now);
    }

    @Test(expected = OfflineResponseProcessorException.class )
    public void shouldRejectWhenResponseSeemsToReferToOtherRequest() {
        // make a pending request dated to 1 millisecond after epoch start
        setUpPendingCertificateSigningRequest();

        TrustAnchorResponse taResponse = getResponseWithSignedCertificates(2L, TA_OBJECTS, TEST_SIGN_RESPONSE);
        when(certificateAuthorityRepository.findAllResourcesCAByName(CA_NAME)).thenReturn(allResourcesCA);

        subject.process(taResponse);
    }

    @Test(expected = OfflineResponseProcessorException.class )
    public void shouldRejectWhenNoPendingRequestFound() {

        TrustAnchorResponse taResponse = getResponseWithSignedCertificates(1L, TA_OBJECTS, TEST_SIGN_RESPONSE);
        when(certificateAuthorityRepository.findAllResourcesCAByName(CA_NAME)).thenReturn(allResourcesCA);

        subject.process(taResponse);
    }

    @Test
    public void shouldNotifyACAAboutRevokedKeys() {
        String pubKeyIdentifier = "CN=testkey";
        TrustAnchorResponse combinedResponse = getResponseWithRevocationResponse(1L, TA_OBJECTS, pubKeyIdentifier);

        // make sure there is a pending request
        setUpPendingRevocationRequest();

        // expect revocation
        when(certificateAuthorityRepository.findAllResourcesCAByName(CA_NAME)).thenReturn(allResourcesCA);

        // expect that the pending request is revoked
        entityManager.remove(isA(UpStreamCARequestEntity.class));
        entityManager.flush();

        assertThatThrownBy(() -> subject.process(combinedResponse))
                .isInstanceOf(CertificateAuthorityException.class)
                .hasMessage("Unknown encoded key: " + pubKeyIdentifier);
        // See integration tests for proper handling of known keys...
    }

    @Test
    public void shouldRemoveRequestOnErrorResponse() {
        TrustAnchorResponse taResponse = getResponseWithErrorResponse(1L, TA_OBJECTS);

        // make sure there is a pending request
        setUpPendingRevocationRequest();

        when(certificateAuthorityRepository.findAllResourcesCAByName(CA_NAME)).thenReturn(allResourcesCA);

        // expect that the pending request is removed
        entityManager.remove(isA(UpStreamCARequestEntity.class));
        entityManager.flush();

        subject.process(taResponse);

        assertThat(allResourcesCA.getUpStreamCARequestEntity()).isNull();
    }

    private void setUpPendingRevocationRequest() {
        KeyPairEntity testKeyPair = TestObjects.createTestKeyPair();
        TaRequest revokeKeyRequest = new RevocationRequest("test resource class", testKeyPair.getEncodedKeyIdentifier());
        List<TaRequest> requests = new ArrayList<>();
        requests.add(revokeKeyRequest);
        TrustAnchorRequest trustAnchorRequest = new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"),
                new X509CertificateInformationAccessDescriptor[0], requests);
        UpStreamCARequestEntity upStreamCARequestEntity = new UpStreamCARequestEntity(allResourcesCA, trustAnchorRequest);
        allResourcesCA.setUpStreamCARequestEntity(upStreamCARequestEntity);
    }

    private void setUpPendingCertificateSigningRequest() {
        UpStreamCARequestEntity upStreamCARequestEntity = createUpStreamCARequestEntity(allResourcesCA);
        allResourcesCA.setUpStreamCARequestEntity(upStreamCARequestEntity);
    }

    private UpStreamCARequestEntity createUpStreamCARequestEntity(AllResourcesCertificateAuthority ca) {
        return new UpStreamCARequestEntity(ca, createTrustAnchorRequest(ca));
    }

    private TrustAnchorRequest createTrustAnchorRequest(AllResourcesCertificateAuthority ca) {
        KeyPairEntity testKeyPair = TestObjects.createTestKeyPair();
        ResourceCertificateRequestData resourceCertificateRequest = ResourceCertificateRequestData.forTASigningRequest(
                "test resource class",
                ca.getName(),
                testKeyPair.getPublicKey().getEncoded(),
                TestObjects.SUBJECT_INFORMATION_ACCESS
        );
        TaRequest signRequest = new SigningRequest(resourceCertificateRequest);
        List<TaRequest> requests = new ArrayList<>();
        requests.add(signRequest);
        return new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"),
                TestObjects.SUBJECT_INFORMATION_ACCESS, requests);
    }

    public static TrustAnchorResponse getResponseWithSignedCertificates(Long serial, Map<URI, CertificateRepositoryObject> publishedObjects, SigningResponse response) {

        TrustAnchorResponse.Builder builder = TrustAnchorResponse.newBuilder(serial);

        builder.addPublishedObjects(publishedObjects);
        builder.addTaResponse(response);

        return builder.build();
    }

    public static TrustAnchorResponse getResponseWithErrorResponse(Long serial, Map<URI, CertificateRepositoryObject> publishedObjects) {
        TrustAnchorResponse.Builder builder = TrustAnchorResponse.newBuilder(serial);

        builder.addPublishedObjects(publishedObjects);
        builder.addTaResponse(new ErrorResponse(UUID.randomUUID(), "User cancelled to proceed. Request will not be processed."));

        return builder.build();
    }

    public static TrustAnchorResponse getResponseWithRevocationResponse(Long serial, Map<URI, CertificateRepositoryObject> publishedObjects, String encodedKey) {
        TrustAnchorResponse.Builder builder = TrustAnchorResponse.newBuilder(serial);

        RevocationResponse revocationResponse = new RevocationResponse(UUID.randomUUID(), "test resource class", encodedKey);

        builder.addPublishedObjects(publishedObjects);
        builder.addTaResponse(revocationResponse);

        return builder.build();
    }

}
