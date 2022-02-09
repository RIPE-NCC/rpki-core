package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagementRevokeOldKeysCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private KeyPairDeletionService keyPairDeletionService;
    @Mock
    private AllResourcesCertificateAuthority allResourcesCa;
    @Mock
    private ProductionCertificateAuthority productionCa;
    @Mock
    private CustomerCertificateAuthority memberCa;
    @Mock
    private CertificateRequestCreationService certificateRequestCreationService;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private PublishedObjectRepository publishedObjectRepository;

    private final long membershipId = 43L;
    private final long acaId = 42L;

    private KeyManagementRevokeOldKeysCommandHandler subject;

    @Before
    public void setUpMocks() {
        when(allResourcesCa.isAllResourcesCa()).thenReturn(true);
        when(memberCa.getParent()).thenReturn(productionCa);
        when(memberCa.isAllResourcesCa()).thenReturn(false);

        subject = new KeyManagementRevokeOldKeysCommandHandler(certificateAuthorityRepository, keyPairDeletionService,
                certificateRequestCreationService, publishedObjectRepository, resourceCertificateRepository);
    }

    @Test
    public void should_return_correct_command_type() {
        assertEquals(KeyManagementRevokeOldKeysCommand.class, subject.commandType());
    }


    @Test
    public void should_throw_command_without_effect_exception_when_there_is_nothing_to_revoke() {

        when(allResourcesCa.getVersionedId()).thenReturn(new VersionedId(acaId));
        when(certificateAuthorityRepository.findHostedCa(acaId)).thenReturn(allResourcesCa);
        when(allResourcesCa.requestOldKeysRevocation()).thenReturn(new ArrayList<CertificateRevocationRequest>());

        KeyManagementRevokeOldKeysCommand command = new KeyManagementRevokeOldKeysCommand(allResourcesCa.getVersionedId());

        boolean exceptionOccurred = false;
        try {
            subject.handle(command, CommandStatus.create());
        } catch (CommandWithoutEffectException e) {
            exceptionOccurred = true;
        }

        assertTrue(exceptionOccurred);
    }

    @Test
    public void should_set_upstream_request_for_ta_for_all_resource_ca() {
        when(allResourcesCa.getVersionedId()).thenReturn(new VersionedId(acaId));
        when(certificateAuthorityRepository.findHostedCa(acaId)).thenReturn(allResourcesCa);

        PublicKey publicKey = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic();
        ArrayList<CertificateRevocationRequest> revocationRequests = new ArrayList<>();
        revocationRequests.add(new CertificateRevocationRequest(publicKey));

        when(allResourcesCa.requestOldKeysRevocation()).thenReturn(revocationRequests);

        subject.handle(new KeyManagementRevokeOldKeysCommand(allResourcesCa.getVersionedId()), any(CommandStatus.class));

        verify(allResourcesCa).setUpStreamCARequestEntity(isA(UpStreamCARequestEntity.class));
    }

    @Test
    public void member_ca_should_request_revocation_of_old_keys() {
        CertificateRevocationResponse response = new CertificateRevocationResponse(DEFAULT_RESOURCE_CLASS, TestObjects.TEST_KEY_PAIR_2.getPublicKey());

        when(certificateAuthorityRepository.findHostedCa(membershipId)).thenReturn(memberCa);

        CertificateRevocationRequest request = createRevocationRequest();
        when(memberCa.requestOldKeysRevocation()).thenReturn(Collections.singletonList(request));
        when(productionCa.processCertificateRevocationRequest(request, resourceCertificateRepository)).thenReturn(response);

        subject.handle(new KeyManagementRevokeOldKeysCommand(new VersionedId(membershipId)), any(CommandStatus.class));

        verify(memberCa).processCertificateRevocationResponse(response, publishedObjectRepository, keyPairDeletionService);
    }

    private CertificateRevocationRequest createRevocationRequest() {
        PublicKey publicKey = TestObjects.createTestKeyPair().getPublicKey();
        return new CertificateRevocationRequest(publicKey);
    }

}
