package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilder;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParser;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.DeleteNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.services.impl.DeleteCertificateAuthorityService;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class DeleteNonHostedCertificateAuthorityCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private ProductionCertificateAuthority productionCA;

    private DeleteNonHostedCertificateAuthorityCommandHandler commandHandler;
    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    private static final long NON_HOSTED_CA_ID = 10L;

    @Before
    public void setup() throws Exception {
        commandHandler = new DeleteNonHostedCertificateAuthorityCommandHandler(certificateAuthorityRepository, new DeleteCertificateAuthorityService(certificateAuthorityRepository, null, null, null, null, null, null, null));
        nonHostedCertificateAuthority = getNonHostedCertificateAuthority();

        when(certificateAuthorityRepository.findNonHostedCa(NON_HOSTED_CA_ID)).thenReturn(nonHostedCertificateAuthority);
    }

    @Test
    public void shouldDeleteCaFromTheRepository() {
        commandHandler.handle(new DeleteNonHostedCertificateAuthorityCommand(new VersionedId(NON_HOSTED_CA_ID)));

        verify(certificateAuthorityRepository).remove(nonHostedCertificateAuthority);
    }

    private NonHostedCertificateAuthority getNonHostedCertificateAuthority() throws Exception {
        KeyPair identityKeyPair = new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate();

        ProvisioningIdentityCertificateBuilder builder = new ProvisioningIdentityCertificateBuilder();
        builder.withSelfSigningKeyPair(new KeyPairFactory(X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER).generate());
        builder.withSelfSigningSubject(new X500Principal("CN=" + KeyPairUtil.getAsciiHexEncodedPublicKeyHash(identityKeyPair.getPublic())));
        ProvisioningIdentityCertificate identityCertificate = builder.build();

        NonHostedCertificateAuthority nonHostedCertificateAuthority = new NonHostedCertificateAuthority(
            NON_HOSTED_CA_ID, new X500Principal("CN=101"), identityCertificate, productionCA);

        PKCS10CertificationRequest certificate = TestObjects.getPkcs10CertificationRequest(URI.create("rsync://tmp/repo"));
        RpkiCaCertificateRequestParser parsedCertificate = new RpkiCaCertificateRequestParser(certificate);

        nonHostedCertificateAuthority.findOrCreatePublicKeyEntityByPublicKey(parsedCertificate.getPublicKey());

        return nonHostedCertificateAuthority;
    }
}
