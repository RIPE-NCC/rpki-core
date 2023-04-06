package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilder;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParser;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParserException;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeleteCertificateAuthorityCommandHandlerTest {
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private KeyPairDeletionService keyPairDeletionService;
    @Mock
    private CommandAuditService commandAuditService;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private PublishedObjectRepository publishedObjectRepository;
    @Mock
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    @Mock
    private RoaConfigurationRepository roaConfigurationRepository;
    @InjectMocks
    private DeleteCertificateAuthorityCommandHandler subject;

    @Mock
    private ParentCertificateAuthority parentCA;

    private final long HOSTED_CA_ID = 101L;
    @Mock
    private KeyPairEntity keyPair;

    private PublicKey publicKey = TEST_KEY_PAIR.getPublic();

    private static final long NON_HOSTED_CA_ID = 10L;


    @Test
    public void should_delete_managed_ca() {
        ManagedCertificateAuthority hostedCA = new HostedCertificateAuthority(HOSTED_CA_ID, new X500Principal("CN=managed"), UUID.randomUUID(), parentCA);
        hostedCA.addKeyPair(keyPair);

        when(certificateAuthorityRepository.get(HOSTED_CA_ID)).thenReturn(hostedCA);
        when(keyPair.getEncodedKeyIdentifier()).thenReturn(KeyPairUtil.getEncodedKeyIdentifier(publicKey));
        when(keyPair.getPublicKey()).thenReturn(publicKey);
        when(keyPair.isCurrent()).thenReturn(true);
        when(parentCA.processCertificateRevocationRequest(new CertificateRevocationRequest(publicKey), resourceCertificateRepository))
            .thenReturn(new CertificateRevocationResponse(publicKey));
        when(roaConfigurationRepository.findByCertificateAuthority(hostedCA)).thenReturn(Optional.empty());

        subject.handle(new DeleteCertificateAuthorityCommand(new VersionedId(HOSTED_CA_ID), new X500Principal("CN=managed")));

        verify(keyPair).revoke(keyPairDeletionService);

        verify(roaAlertConfigurationRepository).findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        verify(roaConfigurationRepository).findByCertificateAuthority(hostedCA);
        verify(commandAuditService).deleteCommandsForCa(HOSTED_CA_ID);
        verify(certificateAuthorityRepository).remove(hostedCA);
    }

    @Test
    public void should_delete_non_hosted_ca() throws Exception {
        NonHostedCertificateAuthority nonHostedCertificateAuthority = getNonHostedCertificateAuthority();

        when(certificateAuthorityRepository.get(NON_HOSTED_CA_ID)).thenReturn(nonHostedCertificateAuthority);
        subject.handle(new DeleteCertificateAuthorityCommand(new VersionedId(NON_HOSTED_CA_ID), new X500Principal("CN=delegated")));

        verify(commandAuditService).deleteCommandsForCa(NON_HOSTED_CA_ID);
        verify(certificateAuthorityRepository).remove(nonHostedCertificateAuthority);
    }

    private NonHostedCertificateAuthority getNonHostedCertificateAuthority() throws RpkiCaCertificateRequestParserException {
        KeyPair identityKeyPair = new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate();

        ProvisioningIdentityCertificateBuilder builder = new ProvisioningIdentityCertificateBuilder();
        builder.withSelfSigningKeyPair(new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate());
        builder.withSelfSigningSubject(new X500Principal("CN=" + KeyPairUtil.getAsciiHexEncodedPublicKeyHash(identityKeyPair.getPublic())));
        ProvisioningIdentityCertificate identityCertificate = builder.build();

        NonHostedCertificateAuthority nonHostedCertificateAuthority = new NonHostedCertificateAuthority(
            NON_HOSTED_CA_ID, new X500Principal("CN=101"), identityCertificate, parentCA);

        PKCS10CertificationRequest certificate = TestObjects.getPkcs10CertificationRequest(URI.create("rsync://tmp/repo"));
        RpkiCaCertificateRequestParser parsedCertificate = new RpkiCaCertificateRequestParser(certificate);

        nonHostedCertificateAuthority.findOrCreatePublicKeyEntityByPublicKey(parsedCertificate.getPublicKey());

        return nonHostedCertificateAuthority;
    }
}