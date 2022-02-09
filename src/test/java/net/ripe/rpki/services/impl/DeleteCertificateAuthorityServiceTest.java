package net.ripe.rpki.services.impl;

import junit.framework.TestCase;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeleteCertificateAuthorityServiceTest extends TestCase {
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private RoaEntityRepository roaEntityRepository;
    @Mock
    private ManifestEntityRepository manifestEntityRepository;
    @Mock
    private CrlEntityRepository crlEntityRepository;
    @Mock
    private CommandAuditService commandAuditService;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private PublishedObjectRepository publishedObjectRepository;
    @Mock
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @InjectMocks
    private DeleteCertificateAuthorityService subject;

    private final long HOSTED_CA_ID = 101l;
    private final X500Principal name = new X500Principal("CN=101");
    @Mock
    private ParentCertificateAuthority parentCA;
    @Mock
    private KeyPairEntity keyPair;
    @Mock
    private RoaEntity roaEntity;
    @Mock
    private ManifestEntity manifestEntity;
    @Mock
    private CrlEntity crlEntity;
    @Mock
    OutgoingResourceCertificate outgoingResourceCertificate;

    @Mock
    PublicKey publicKey;

    @Test
    public void testHandleDeleteCA() {

        VersionedId caId = new VersionedId(HOSTED_CA_ID);
        DeleteCertificateAuthorityCommand command = new DeleteCertificateAuthorityCommand(caId, new X500Principal("CN=Test"), new RoaConfigurationData(new ArrayList<>()));
        HostedCertificateAuthority hostedCA = new CustomerCertificateAuthority(HOSTED_CA_ID, name, parentCA, 1);
        hostedCA.addKeyPair(keyPair);

        when(certificateAuthorityRepository
                .findHostedCa(HOSTED_CA_ID))
                .thenReturn(hostedCA);

        when(roaEntityRepository
                .findByCertificateSigningKeyPair(keyPair))
                .thenReturn(Collections.singletonList(roaEntity));

        when(manifestEntityRepository
                .findByKeyPairEntity(keyPair))
                .thenReturn(manifestEntity);

        when(crlEntityRepository
                .findByKeyPair(keyPair))
                .thenReturn(crlEntity);

        when(roaAlertConfigurationRepository
                .findByCertificateAuthorityIdOrNull(anyLong()))
                .thenReturn(null);

        when(resourceCertificateRepository
                .findCurrentCertificatesBySubjectPublicKey(publicKey))
                .thenReturn(Collections.singletonList(outgoingResourceCertificate));

        when(resourceCertificateRepository
                .findAllBySigningKeyPair(keyPair))
                .thenReturn(Collections.singletonList(outgoingResourceCertificate));

        when(keyPair.getPublicKey())
                .thenReturn(publicKey);

        subject.deleteCa(HOSTED_CA_ID);

        verify(roaEntityRepository).findByCertificateSigningKeyPair(keyPair);
        verify(roaEntityRepository).remove(roaEntity);

        verify(manifestEntityRepository).findByKeyPairEntity(keyPair);
        verify(manifestEntityRepository).remove(manifestEntity);

        verify(crlEntityRepository).findByKeyPair(keyPair);
        verify(crlEntityRepository).remove(crlEntity);

        verify(resourceCertificateRepository).findCurrentCertificatesBySubjectPublicKey(publicKey);
        verify(outgoingResourceCertificate).revoke();

        verify(resourceCertificateRepository).findAllBySigningKeyPair(keyPair);
        verify(resourceCertificateRepository).remove(outgoingResourceCertificate);

        verify(publishedObjectRepository).withdrawAllForDeletedKeyPair(keyPair);

        verify(keyPair).deleteIncomingResourceCertificate();

        verify(roaAlertConfigurationRepository).findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        verify(certificateAuthorityRepository).remove(hostedCA);
        verify(commandAuditService).deleteCommandsForCa(HOSTED_CA_ID);
    }
}