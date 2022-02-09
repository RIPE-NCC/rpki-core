package net.ripe.rpki.ui.admin;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsTest;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsTest;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.crl.X509CrlTest;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.dto.ResourceClassData;
import net.ripe.rpki.server.api.dto.ResourceClassDataSet;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.domain.response.RevocationResponse;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorResponseSerializer;
import net.ripe.rpki.ui.application.AbstractCertificationWicketTest;
import org.apache.commons.io.FileUtils;
import org.apache.wicket.util.file.File;
import org.apache.wicket.util.tester.FormTester;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest.createSelfSignedCaResourceCertificate;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ALL_RESOURCES;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.isA;
import static org.mockito.BDDMockito.verify;

public class UpstreamCaManagementPageTest extends AbstractCertificationWicketTest {

    public static final URI REPO_LOCATION = URI.create("rsync://repository/location/");

    public static final String CREATE_ALL_RESOURCES_FORM = "createAllResourcesCertificateAuthorityForm";
    public static final String DOWNLOAD_PENDING_REQUEST = "pendingRequestOrManagementPanel:content:downloadLink";
    public static final String UPLOAD_RESPONSE = "pendingRequestOrManagementPanel:content:offlineResponseUploadForm";

    public static final String GENERATE_REPUBLISH_REQUEST = "pendingRequestOrManagementPanel:republish";
    public static final String GENERATE_SIGN_REQUEST = "pendingRequestOrManagementPanel:signRequest";
    public static final String KEY_ROLL_INITIALISE_REQUEST = "pendingRequestOrManagementPanel:manageKeys:initiateRolls";
    public static final String KEY_ROLL_ACTIVATE_REQUEST = "pendingRequestOrManagementPanel:manageKeys:activatePending";
    public static final String KEY_ROLL_REVOKE_REQUEST = "pendingRequestOrManagementPanel:manageKeys:revokeOld";

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        setUpMocksForCurrentCAInitialization();
    }

    @Test
    public void shouldSimplyRender() {
        tester.startPage(UpstreamCaManagementPage.class);
        tester.assertRenderedPage(UpstreamCaManagementPage.class);
    }

    @Test
    public void shouldShowCreateAllResourcesCaForm() {

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertVisible(CREATE_ALL_RESOURCES_FORM);
    }

    @Test
    public void shouldNotShowCreateAllResourcesCaFormIfExists() {
        givenAllResourcesCaExists();
        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertContainsNot(CREATE_ALL_RESOURCES_FORM);
    }

    @Test
    public void shouldShowAndDownloadPendingRequest() {
        givenAllResourcesCaExists(new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"), null, null));


        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertVisible(DOWNLOAD_PENDING_REQUEST);
        tester.clickLink(DOWNLOAD_PENDING_REQUEST, false);
        assertEquals("application/xml", tester.getServletResponse().getContentType());
    }

    @Test
    public void shouldUploadResponse() throws IOException {
        givenAllResourcesCaExists(new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"), null, null));

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertVisible(UPLOAD_RESPONSE);

        TrustAnchorResponse expectedTaResponse = getOfflineTrustAnchorsResponse();
        uploadTaResponse(expectedTaResponse);

        // then should see upstream ca management page
        tester.assertRenderedPage(UpstreamCaManagementPage.class);

        // and page should send the ProcessTrustAnchorResponseCommand to service layer
        ArgumentCaptor<ProcessTrustAnchorResponseCommand> argumentCaptor = ArgumentCaptor.forClass(ProcessTrustAnchorResponseCommand.class);
        verify(commandService).execute(argumentCaptor.capture());
        assertEquals(expectedTaResponse, argumentCaptor.getValue().getOfflineResponse());
    }


    private void uploadTaResponse(TrustAnchorResponse expectedTaResponse) throws IOException {
        File response = new File(folder.newFile("response.xml"));
        FileUtils.writeStringToFile(response, new TrustAnchorResponseSerializer().serialize(expectedTaResponse), Charset.defaultCharset());
        FormTester formTester = tester.newFormTester(UPLOAD_RESPONSE);
        formTester.setFile("offlineResponseUploadFile", response, "application/xml");
        formTester.submit();
    }

    @Test
    public void should_hide_download_and_upload_when_there_is_no_pending_request() {
        givenCaWithoutPendingRequest();

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertContainsNot(DOWNLOAD_PENDING_REQUEST);
        tester.assertContainsNot(UPLOAD_RESPONSE);
    }

    @Test
    public void should_disable_republish_button_when_there_is_pending_request() {
        givenCaWithPendingRequest(new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"), null, null));

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertContainsNot(GENERATE_REPUBLISH_REQUEST);
    }

    @Test
    public void should_allow_sign_request_when_there_is_no_pending_request() {
        givenAllResourcesCaExists();
        givenCurrentKeyExists();

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertVisible(GENERATE_SIGN_REQUEST);

        tester.clickLink(GENERATE_SIGN_REQUEST);
        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        verify(commandService).execute(isA(AllResourcesCaResourcesCommand.class));
    }

    @Test
    public void should_disable_sign_request_button_when_there_is_pending_request() {
        givenCaWithPendingRequest(new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"), null, null));

        tester.startPage(UpstreamCaManagementPage.class);

        tester.assertRenderedPage(UpstreamCaManagementPage.class);
        tester.assertContainsNot(GENERATE_SIGN_REQUEST);
    }

    private void givenCurrentKeyExists() {
        List<KeyPairData> keys = Collections.singletonList(new KeyPairData(1L, "currentKey", "currentKeyStore",
            KeyPairStatus.CURRENT, new DateTime(), null, REPO_LOCATION, "key1.crl", "key1.mft", false));

        IpResourceSet resources = new IpResourceSet(RESOURCES);
        resources.addAll(IpResourceSet.IP_PRIVATE_USE_RESOURCES);

        CertificateAuthorityData productionCa = new HostedCertificateAuthorityData(PRODUCTION_CA_VERSIONED_ID,
            PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), CertificateAuthorityType.ROOT, resources, keys);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(productionCa);
    }

    private void givenNewKeyExists() {
        List<KeyPairData> keys = Collections.singletonList(new KeyPairData(1L, "newKey", "currentKeyStore",
            KeyPairStatus.NEW, new DateTime(), null, REPO_LOCATION, "key1.crl", "key1.mft", false));

        IpResourceSet resources = new IpResourceSet(RESOURCES);
        resources.addAll(IpResourceSet.IP_PRIVATE_USE_RESOURCES);

        CertificateAuthorityData productionCa = new HostedCertificateAuthorityData(PRODUCTION_CA_VERSIONED_ID,
            PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), CertificateAuthorityType.ROOT, resources, keys);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(productionCa);
    }

    private void givenPendingAndCurrentKeysExist() {
        List<KeyPairData> keys = new ArrayList<>();
        keys.add(new KeyPairData(1L, "currentKey", "currentKeyStore", KeyPairStatus.CURRENT,
            new DateTime().minusMonths(1), null, REPO_LOCATION, "key1.crl", "key1.mft", false));
        keys.add(new KeyPairData(1L, "pendingKey", "pendingKeyStore", KeyPairStatus.PENDING,
            new DateTime(), null, REPO_LOCATION, "key2.crl", "key2.mft", false));

        IpResourceSet resources = new IpResourceSet(RESOURCES);
        resources.addAll(IpResourceSet.IP_PRIVATE_USE_RESOURCES);
        CertificateAuthorityData productionCa = new HostedCertificateAuthorityData(PRODUCTION_CA_VERSIONED_ID, PRODUCTION_CA_PRINCIPAL,
            UUID.randomUUID(), CertificateAuthorityType.ROOT, resources, keys);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(productionCa);
    }

    private void givenOldAndCurrentKeysExist() {
        List<KeyPairData> keys = new ArrayList<>();
        keys.add(new KeyPairData(1L, "oldKey", "oldKeyStore", KeyPairStatus.OLD, new DateTime().minusMonths(1), null, REPO_LOCATION, "key1.crl", "key1.mft", false));
        keys.add(new KeyPairData(1L, "currentKey", "currentKeyStore", KeyPairStatus.CURRENT, new DateTime(), null, REPO_LOCATION, "key2.crl", "key2.mft", false));

        IpResourceSet resources = new IpResourceSet(RESOURCES);
        resources.addAll(IpResourceSet.IP_PRIVATE_USE_RESOURCES);

        CertificateAuthorityData productionCa = new HostedCertificateAuthorityData(PRODUCTION_CA_VERSIONED_ID,
            PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), CertificateAuthorityType.ROOT, resources, keys);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(productionCa);
    }

    private void givenCaWithPendingRequest(TrustAnchorRequest trustAnchorRequest) {
        CertificateAuthorityData productionCa = new HostedCertificateAuthorityData(PRODUCTION_CA_VERSIONED_ID,
            PRODUCTION_CA_PRINCIPAL, UUID.randomUUID(), CertificateAuthorityType.ROOT, RESOURCES,
            trustAnchorRequest, Collections.emptyList());
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(productionCa);
    }

    private void givenAllResourcesCaExists(TrustAnchorRequest trustAnchorRequest) {
        final long ALL_RESOURCES_CA_ID = 43l;
        final VersionedId ALL_RESOURCES_CA_VERSIONED_ID = new VersionedId(ALL_RESOURCES_CA_ID, 3);

        final CertificateAuthorityData allResourcesCertificateAuthorityData = new HostedCertificateAuthorityData(
            ALL_RESOURCES_CA_VERSIONED_ID, ALL_RESOURCES_CA_PRINCIPAL, UUID.randomUUID(), ALL_RESOURCES,
            new IpResourceSet(), trustAnchorRequest, Collections.emptyList());

        given(caViewService.findCertificateAuthorityByName(ALL_RESOURCES_CA_PRINCIPAL)).willReturn(allResourcesCertificateAuthorityData);
    }

    private void givenAllResourcesCaExists() {
        givenAllResourcesCaExists(null);
    }

    private void givenCaWithoutPendingRequest() {
        givenCaWithPendingRequest(null);
    }

    private TrustAnchorResponse getOfflineTrustAnchorsResponse() {
        X509ResourceCertificate newCertificate = createSelfSignedCaResourceCertificate(IpResourceSet.ALL_PRIVATE_USE_RESOURCES);
        URI newCertificateUri = URI.create("rsync://nowhere/res.cer");
        X509Crl crl = X509CrlTest.createCrl();
        RoaCms roa = RoaCmsTest.getRoaCms();
        ManifestCms mft = ManifestCmsTest.getRootManifestCms();

        SigningResponse rtaSigningResponse = new SigningResponse(UUID.randomUUID(), "test resource class", newCertificateUri, newCertificate);

        RevocationResponse revocationResponse = new RevocationResponse(UUID.randomUUID(), "test resource class", "encoded");

        TrustAnchorResponse.Builder trustAnchorResponseBuilder = TrustAnchorResponse.newBuilder(new DateTime().getMillis());
        trustAnchorResponseBuilder.addTaResponse(rtaSigningResponse);
        trustAnchorResponseBuilder.addTaResponse(revocationResponse);

        trustAnchorResponseBuilder.addPublishedObject(URI.create("rsync://somewhere/a.cer"), newCertificate);
        trustAnchorResponseBuilder.addPublishedObject(URI.create("rsync://somewhere/b.crl"), crl);
        trustAnchorResponseBuilder.addPublishedObject(URI.create("rsync://somewhere/c.roa"), roa);
        trustAnchorResponseBuilder.addPublishedObject(URI.create("rsync://somewhere/d.mft"), mft);

        return trustAnchorResponseBuilder.build();
    }
}
