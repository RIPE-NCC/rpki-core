package net.ripe.rpki.services.impl;

import junit.framework.TestCase;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeleteCertificateAuthorityServiceTest extends TestCase {
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
    private DeleteCertificateAuthorityService subject;

    private final long HOSTED_CA_ID = 101L;
    private final X500Principal name = new X500Principal("CN=101");
    @Mock
    private ParentCertificateAuthority parentCA;

    @Mock
    private KeyPairEntity keyPair;

    private PublicKey publicKey = TEST_KEY_PAIR.getPublic();

    @Test
    public void testHandleDeleteCA() {

        VersionedId caId = new VersionedId(HOSTED_CA_ID);
        RoaConfiguration roaConfiguration = new RoaConfiguration();
        DeleteCertificateAuthorityCommand command = new DeleteCertificateAuthorityCommand(caId, new X500Principal("CN=Test"), new RoaConfigurationData(new ArrayList<>()));
        ManagedCertificateAuthority hostedCA = new HostedCertificateAuthority(HOSTED_CA_ID, name, parentCA);
        hostedCA.addKeyPair(keyPair);

        when(keyPair.getEncodedKeyIdentifier()).thenReturn(KeyPairUtil.getEncodedKeyIdentifier(publicKey));
        when(parentCA.processCertificateRevocationRequest(new CertificateRevocationRequest(publicKey), resourceCertificateRepository))
                .thenReturn(new CertificateRevocationResponse(DEFAULT_RESOURCE_CLASS, publicKey));

        when(certificateAuthorityRepository
                .findManagedCa(HOSTED_CA_ID))
                .thenReturn(hostedCA);

        when(roaAlertConfigurationRepository
                .findByCertificateAuthorityIdOrNull(anyLong()))
                .thenReturn(null);
        when(roaConfigurationRepository.findByCertificateAuthority(hostedCA))
            .thenReturn(Optional.of(roaConfiguration));

        when(keyPair.getPublicKey())
                .thenReturn(publicKey);
        when(keyPair.isCurrent()).thenReturn(true);

        subject.revokeCa(HOSTED_CA_ID);

        verify(keyPair).deleteIncomingResourceCertificate();
        verify(keyPair).requestRevoke();
        verify(keyPair).revoke(publishedObjectRepository);
        verify(keyPairDeletionService).deleteRevokedKeysFromResponses(
            hostedCA,
            Collections.singletonList(new CertificateRevocationResponse(DEFAULT_RESOURCE_CLASS, publicKey))
        );

        verify(commandAuditService).deleteCommandsForCa(HOSTED_CA_ID);
        verify(roaAlertConfigurationRepository).findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        verify(roaConfigurationRepository).remove(roaConfiguration);
        verify(certificateAuthorityRepository).remove(hostedCA);
    }
}