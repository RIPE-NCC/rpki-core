package net.ripe.rpki.rest.service;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper;
import net.ripe.rpki.commons.provisioning.identity.ChildIdentitySerializer;
import net.ripe.rpki.commons.provisioning.identity.ParentIdentity;
import net.ripe.rpki.commons.provisioning.identity.ParentIdentitySerializer;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilder;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaServiceTest {
    @MockBean
    private CertificateAuthorityCreateService certificateAuthorityCreateService;

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private ProvisioningIdentityViewService delegationCaProvisioningService;

    @MockBean
    private ResourceLookupService resourceCache;

    @MockBean
    private CommandService commandService;

    @Autowired
    private MockMvc mockMvc;

    private static final String IDENTITY_CERTIFICATE_XML =
            "<ns0:child_request xmlns:ns0=\"http://www.hactrn.net/uris/rpki/rpki-setup/\" child_handle=\"Bob\" version=\"1\">\n" +
                    "<ns0:child_bpki_ta>\n" +
                    "MIIDIDCCAgigAwIBAgIBATANBgkqhkiG9w0BAQsFADApMScwJQYDVQQDEx5Cb2Ig\n" +
                    "QlBLSSBSZXNvdXJjZSBUcnVzdCBBbmNob3IwHhcNMTEwNzAxMDQwNzIzWhcNMTIw\n" +
                    "NjMwMDQwNzIzWjApMScwJQYDVQQDEx5Cb2IgQlBLSSBSZXNvdXJjZSBUcnVzdCBB\n" +
                    "bmNob3IwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDEk1f7cVzHu3r/\n" +
                    "fJ5gkBxnWMNJ1CP0kPtfP8oFOEVVH1lX0MHuFKhdKA4WCkGkeFtGb/4T/nGgsD+z\n" +
                    "exZid6RR8zjHkwMLvAl0x6wdKa46XJbFu+wTSu+vlowVY9rGzH+ttv4Fj6E2Y3DG\n" +
                    "983/dVNJfXl00+Ts7rlvVcn9lI5dWvzsLoUOdhD4hsyKp53k8i4HexiD+0ugPeh9\n" +
                    "4PKiyZOuMjSRNQSBUA3ElqJSRZz7nWvs/j6zhwHdFa+lN56575Mc5mrwr+KePwW5\n" +
                    "DLt3izYpjwKffVuxUKPTrhvnOOg5kBBv0ihync21LSLds6jusxaMYUwUElO8KQyn\n" +
                    "NUAeGPd/AgMBAAGjUzBRMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFORFOC3G\n" +
                    "PjYKn7V1/BJHDmZ4W7J+MB8GA1UdIwQYMBaAFORFOC3GPjYKn7V1/BJHDmZ4W7J+\n" +
                    "MA0GCSqGSIb3DQEBCwUAA4IBAQBqsP4ENtWTkNdsekYB+4hO/Afq20Ho0W8lyTkM\n" +
                    "JO1UFDt/dzFAmTT4uM7pmwuQfqmCYjNDWon8nsdFno4tA0is3aiq6yIMAYzBE5ub\n" +
                    "bnJMxldqLoWuakco1wYa3kZFzWPwecxgJ4ZlqTPGu0Loyjibt25IE9MfixyWDw+D\n" +
                    "MhyfonLLgFb5jz7A3BTE63vlTp359uDbFb1nRdyoT31s3FUBK8jF4B5pWzPiLdct\n" +
                    "bOMVjYUBs8aFC3fDXyGSr/RcjE4OOZQyTkYZn8zCPUJ4KqOPAUV9u9jx2FPvOcA3\n" +
                    "1BjcmhYHqott+cnK1ITOjLe9EKejRZv/7/BFsmpzm2Zbq1KA\n" +
                    "</ns0:child_bpki_ta>\n" +
                    "</ns0:child_request>";

    @Before
    public void init() {
        reset(certificateAuthorityCreateService, certificateAuthorityViewService, resourceCache, commandService);
    }

    @Test
    public void shouldCreateAProperOrgHostedCA() throws Exception {

        final ArgumentCaptor<X500Principal> argument = ArgumentCaptor.forClass(X500Principal.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/ORG-1/hosted"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(certificateAuthorityCreateService).createHostedCertificateAuthority(argument.capture());

        assertEquals(new X500Principal("O=ORG-1"), argument.getValue());
    }

    @Test
    public void revokeHosted_should_revoke_hosted() throws Exception {
        final String caName = "123";
        X500Principal principal = CaName.parse(caName).getPrincipal();

        final CertificateAuthorityData certificateAuthorityData = new HostedCertificateAuthorityData(new VersionedId(1L),
                principal, UUID.randomUUID(), 2L,
                ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(certificateAuthorityData);
        when(commandService.execute(new DeleteCertificateAuthorityCommand(certificateAuthorityData.getVersionedId(), principal))).thenReturn(null);

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/123/hosted"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath(".caName").value(principal.getName()))
                .andExpect(jsonPath(".revoked").value(true))
                .andExpect(jsonPath(".error").doesNotExist());

        then(commandService).should().execute(new DeleteCertificateAuthorityCommand(certificateAuthorityData.getVersionedId(), principal));
    }

    @Test
    public void revokeHosted_should_not_revoke_other_type() throws Exception {
        final String caName = "123";
        X500Principal principal = CaName.parse(caName).getPrincipal();

        final CertificateAuthorityData certificateAuthorityData = new ManagedCertificateAuthorityData(new VersionedId(1L),
                principal, UUID.randomUUID(), 2L, CertificateAuthorityType.ROOT,
                ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(certificateAuthorityData);
        when(commandService.execute(
                new DeleteCertificateAuthorityCommand(certificateAuthorityData.getVersionedId(), principal)
        )).thenReturn(null);

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/123/hosted"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(".caName").value(principal.getName()))
                .andExpect(jsonPath(".revoked").value(false))
                .andExpect(jsonPath(".error").isNotEmpty());

        then(commandService).shouldHaveNoInteractions();
    }

    @Test
    public void revokeHosted_should_handle_exception() throws Exception {
        final String caName = "123";
        X500Principal principal = CaName.parse(caName).getPrincipal();

        final CertificateAuthorityData certificateAuthorityData = new HostedCertificateAuthorityData(new VersionedId(1L),
                principal, UUID.randomUUID(), 2L,
                ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(certificateAuthorityData);
        when(commandService.execute(
                new DeleteCertificateAuthorityCommand(certificateAuthorityData.getVersionedId(), principal)
        )).thenThrow(new IllegalStateException("REASON"));

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/123/hosted"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath(".caName").value(principal.getName()))
                .andExpect(jsonPath(".revoked").doesNotExist())
                .andExpect(jsonPath(".error").isNotEmpty());

        then(commandService).should().execute(new DeleteCertificateAuthorityCommand(certificateAuthorityData.getVersionedId(), principal));
    }

    @Test
    public void revokeHosted_should_handle_missing() throws Exception {
        final String caName = "123";
        X500Principal principal = CaName.parse(caName).getPrincipal();

        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(null);

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/123/hosted"))
                .andExpect(status().isNotFound());

        then(commandService).shouldHaveNoInteractions();
    }

    @Test
    public void shouldCreateAProperMemberHostedCA() throws Exception {

        final ArgumentCaptor<X500Principal> argument = ArgumentCaptor.forClass(X500Principal.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/hosted"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON));

        verify(certificateAuthorityCreateService).createHostedCertificateAuthority(argument.capture());

        assertEquals(new X500Principal("CN=123"), argument.getValue());

    }

    @Test
    public void shouldProvideCASummary() throws Exception {
        X500Principal principal = CaName.parse("123").getPrincipal();

        CertificateAuthorityData certificateAuthorityData = new ManagedCertificateAuthorityData(new VersionedId(1L),
                new X500Principal("CN=1"), UUID.randomUUID(), 2L, CertificateAuthorityType.HOSTED,
                ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(certificateAuthorityData);
        when(resourceCache.lookupMemberCaPotentialResources(principal)).thenReturn(Optional.of(ResourceExtension.ofResources(ImmutableResourceSet.parse("10/8"))));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.summary.instantiated").value("true"))
                .andExpect(jsonPath("$.summary.hasCertifiableResources").value("true"))
                .andExpect(jsonPath("$.summary.hosted").value("true"));
    }

    @Test
    public void shouldProvideCASummaryForNonExistingCAs() throws Exception {
        X500Principal principal = CaName.parse("123").getPrincipal();

        when(certificateAuthorityViewService.findCertificateAuthorityIdByName(principal)).thenReturn(null);
        when(resourceCache.lookupMemberCaPotentialResources(principal)).thenReturn(Optional.of(ResourceExtension.ofResources(ImmutableResourceSet.parse("10/8"))));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.summary.instantiated").value("false"));
    }

    @Test
    public void shouldCreateNonHostedCA() throws Exception {
        ArgumentCaptor<X500Principal> principalArgument = ArgumentCaptor.forClass(X500Principal.class);
        ArgumentCaptor<ProvisioningIdentityCertificate> certificateArgument = ArgumentCaptor.forClass(ProvisioningIdentityCertificate.class);

        RequestBuilder req = Rest.multipart(
                API_URL_PREFIX + "/ORG-1/non-hosted",
                "file", IDENTITY_CERTIFICATE_XML.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(req)
            .andExpect(status().isCreated())
            .andExpect(content().contentType(APPLICATION_JSON));

        verify(certificateAuthorityCreateService).createNonHostedCertificateAuthority(principalArgument.capture(), certificateArgument.capture());
        X500Principal x500Principal = principalArgument.getValue();
        ProvisioningIdentityCertificate certificate = certificateArgument.getValue();

        assertEquals("O=ORG-1", x500Principal.getName());
        assertEquals(new ChildIdentitySerializer().deserialize(IDENTITY_CERTIFICATE_XML).getIdentityCertificate(), certificate);
    }

    @Test
    public void shouldProperlyRespondWhenCreateExisting_non_hosted_ca() throws Exception {
        doThrow(new CertificateAuthorityNameNotUniqueException(new X500Principal("CN=ORG-1")))
                .when(certificateAuthorityCreateService)
                .createNonHostedCertificateAuthority(any(), any());

        RequestBuilder req = Rest.multipart(
                API_URL_PREFIX + "/ORG-1/non-hosted",
                "file", IDENTITY_CERTIFICATE_XML.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(req)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("ORG-1")));
    }

    @Test
    public void shouldProperlyRespondWhenCreateExisting_hosted_ca() throws Exception {
        doThrow(new CertificateAuthorityNameNotUniqueException(new X500Principal("CN=ORG-1")))
                .when(certificateAuthorityCreateService)
                .createHostedCertificateAuthority(any());

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/ORG-1/hosted"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("ORG-1")));
    }

    @Test
    public void shouldRefuseToCreateWithoutApiKey() throws Exception {
        mockMvc.perform(Rest.postWithoutApiKey(API_URL_PREFIX + "/ORG-1/hosted"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_JSON));
    }

    @Test
    public void shouldCreateNonHostedCAAndRevokeIt() throws Exception {

        NonHostedCertificateAuthorityData ca = mock(NonHostedCertificateAuthorityData.class);

        X500Principal principal = CaName.parse("123").getPrincipal();
        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(ca);
        when(ca.getVersionedId()).thenReturn(VersionedId.parse("111"));

        ArgumentCaptor<DeleteCertificateAuthorityCommand> certificateArgument = ArgumentCaptor.forClass(DeleteCertificateAuthorityCommand.class);

        mockMvc.perform(Rest.delete(API_URL_PREFIX + "/123/non-hosted"))
                .andExpect(status().is(204));

        verify(commandService).execute(certificateArgument.capture());
        DeleteCertificateAuthorityCommand command = certificateArgument.getValue();
        assertEquals(111L, command.getCertificateAuthorityId());
    }

    @Test
    public void shouldDownloadIdentityCertificate() throws Exception {
        KeyPair identityKeyPair = new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate();

        ProvisioningIdentityCertificateBuilder builder = new ProvisioningIdentityCertificateBuilder();
        builder.withSelfSigningKeyPair(new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate());
        builder.withSelfSigningSubject(new X500Principal("CN=" + KeyPairUtil.getAsciiHexEncodedPublicKeyHash(identityKeyPair.getPublic())));
        ProvisioningIdentityCertificate identityCertificate = builder.build();

        final X500Principal principal = CaName.parse("123").getPrincipal();
        ManagedCertificateAuthorityData managedCertificateAuthorityData = new ManagedCertificateAuthorityData(
            VersionedId.parse("1"), principal, UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
            ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList()
        );
        when(certificateAuthorityViewService.findCertificateAuthorityByName(principal)).thenReturn(managedCertificateAuthorityData);

        final ParentIdentity parentId = new ParentIdentity(new URI("http://bla.bla/bla"), "parentHandle", "childHandle", identityCertificate);
        when(delegationCaProvisioningService.getParentIdentityForNonHostedCa(principal)).thenReturn(parentId);

        MvcResult result = mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/issuer-identity")).andReturn();
        final MockHttpServletResponse response = result.getResponse();

        assertEquals(200, response.getStatus());
        String xml = response.getContentAsString();

        ParentIdentity p = new ParentIdentitySerializer().deserialize(xml);
        assertEquals(parentId, p);
    }
}
