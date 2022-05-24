package net.ripe.rpki.services.impl;

import junit.framework.TestCase;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.util.DBComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;

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
    private DBComponent dbComponent;

    @InjectMocks
    private DeleteCertificateAuthorityService subject;

    private final long HOSTED_CA_ID = 101l;
    private final X500Principal name = new X500Principal("CN=101");
    @Mock
    private ParentCertificateAuthority parentCA;

    @Mock
    private KeyPairEntity keyPair;

    private PublicKey publicKey = TEST_KEY_PAIR.getPublic();

    @Test
    public void testHandleDeleteCA() {

        VersionedId caId = new VersionedId(HOSTED_CA_ID);
        DeleteCertificateAuthorityCommand command = new DeleteCertificateAuthorityCommand(caId, new X500Principal("CN=Test"), new RoaConfigurationData(new ArrayList<>()));
        HostedCertificateAuthority hostedCA = new CustomerCertificateAuthority(HOSTED_CA_ID, name, parentCA, 1);
        hostedCA.addKeyPair(keyPair);

        when(keyPair.getEncodedKeyIdentifier()).thenReturn(KeyPairUtil.getEncodedKeyIdentifier(publicKey));
        when(parentCA.processCertificateRevocationRequest(new CertificateRevocationRequest(publicKey), resourceCertificateRepository))
                .thenReturn(new CertificateRevocationResponse(DEFAULT_RESOURCE_CLASS, publicKey));

        when(certificateAuthorityRepository
                .findHostedCa(HOSTED_CA_ID))
                .thenReturn(hostedCA);

        when(roaAlertConfigurationRepository
                .findByCertificateAuthorityIdOrNull(anyLong()))
                .thenReturn(null);

        when(keyPair.getPublicKey())
                .thenReturn(publicKey);

        subject.deleteCa(HOSTED_CA_ID);

        verify(dbComponent).lockAndRefresh(parentCA);

        verify(keyPair).deleteIncomingResourceCertificate();
        verify(keyPair).requestRevoke();
        verify(keyPair).revoke(publishedObjectRepository);
        verify(keyPairDeletionService).deleteRevokedKeysFromResponses(
            hostedCA,
            Collections.singletonList(new CertificateRevocationResponse(DEFAULT_RESOURCE_CLASS, publicKey))
        );

        verify(commandAuditService).deleteCommandsForCa(HOSTED_CA_ID);
        verify(roaAlertConfigurationRepository).findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        verify(certificateAuthorityRepository).remove(hostedCA);
    }
}