package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityInvariantViolationException;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import java.util.Arrays;

import static net.ripe.rpki.domain.CertificationDomainTestCase.CA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class HostedCertificateAuthorityOutgoingResourceCertificatesInvariantHandlerTest {

    private HostedCertificateAuthorityOutgoingResourceCertificatesInvariantHandler subject;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private HostedCertificateAuthority certificateAuthority;

    private KeyPairEntity oldKeyPair;
    private KeyPairEntity currentKeyPair;

    @Before
    public void setUp() {
        subject = new HostedCertificateAuthorityOutgoingResourceCertificatesInvariantHandler(new SimpleMeterRegistry(), entityManager, resourceCertificateRepository);
        when(entityManager.find(HostedCertificateAuthority.class, CA_ID)).thenReturn(certificateAuthority);

        oldKeyPair = mock(KeyPairEntity.class, RETURNS_DEEP_STUBS);
        when(oldKeyPair.isPublishable()).thenReturn(true);
        when(oldKeyPair.getCurrentIncomingCertificate().getResources()).thenReturn(IpResourceSet.parse("10.0.0.0/8"));
        currentKeyPair = mock(KeyPairEntity.class, RETURNS_DEEP_STUBS);
        when(currentKeyPair.isPublishable()).thenReturn(true);
        when(currentKeyPair.getCurrentIncomingCertificate().getResources()).thenReturn(IpResourceSet.parse("10.0.0.0/8"));
        when(certificateAuthority.getKeyPairs()).thenReturn(Arrays.asList(oldKeyPair, currentKeyPair));

        when(resourceCertificateRepository.findCurrentOutgoingChildCertificateResources(certificateAuthority.getName())).thenReturn(IpResourceSet.parse("10.0.0.0/8"));
        when(resourceCertificateRepository.findCurrentOutgoingRpkiObjectCertificateResources(certificateAuthority.getName())).thenReturn(IpResourceSet.parse("10.0.0.0/8"));
    }

    @Test
    public void should_check_incoming_resource_consistency() {
        // All incoming resource certificates should have the same resources
        when(oldKeyPair.getCurrentIncomingCertificate().getResources()).thenReturn(IpResourceSet.parse("10.0.0.0/8"));
        when(currentKeyPair.getCurrentIncomingCertificate().getResources()).thenReturn(IpResourceSet.parse("172.16.0.0/12"));

        // so an CertificateAuthorityInvariantViolationException is thrown when they do not match
        assertThatThrownBy(
            () -> subject.handleInternal(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE))
        ).isInstanceOfSatisfying(CertificateAuthorityInvariantViolationException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("CA certificateAuthority: not all incoming certificates have the same resources: [10.0.0.0/8, 172.16.0.0/12]");
        });
    }

    @Test
    public void should_check_outgoing_child_resource_consistency() {
        // Outgoing child resources should always be contained in incoming resources
        when(resourceCertificateRepository.findCurrentOutgoingChildCertificateResources(certificateAuthority.getName())).thenReturn(IpResourceSet.parse("192.168.0.0/16"));

        // so an CertificateAuthorityInvariantViolationException is thrown when they do not match
        assertThatThrownBy(
            () -> subject.handleInternal(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE))
        ).isInstanceOfSatisfying(CertificateAuthorityInvariantViolationException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("CA certificateAuthority: with current resources 10.0.0.0/8 does not contain issued child resources 192.168.0.0/16");
        });
    }

    @Test
    public void should_check_outgoing_rpki_object_resource_consistency_when_manifest_is_uptodate() {
        // Outgoing RPKI object resources should be contained in incoming resources when manifest/CRL are up-to-date
        when(certificateAuthority.isManifestAndCrlCheckNeeded()).thenReturn(false);
        when(resourceCertificateRepository.findCurrentOutgoingRpkiObjectCertificateResources(certificateAuthority.getName())).thenReturn(IpResourceSet.parse("192.168.0.0/16"));

        // so an CertificateAuthorityInvariantViolationException is thrown when they do not match
        assertThatThrownBy(
            () -> subject.handleInternal(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE))
        ).isInstanceOfSatisfying(CertificateAuthorityInvariantViolationException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("CA certificateAuthority: with current resources 10.0.0.0/8 does not contain issued non-child resources 192.168.0.0/16");
        });
    }

    @Test
    public void should_not_check_outgoing_rpki_object_resource_consistency_when_manifest_is_outdated() {
        // Outgoing RPKI object resources should be contained in incoming resources when manifest/CRL are up-to-date
        when(certificateAuthority.isManifestAndCrlCheckNeeded()).thenReturn(true);

        subject.handle(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE), new CommandStatus());

        verify(resourceCertificateRepository, never()).findCurrentOutgoingRpkiObjectCertificateResources(certificateAuthority.getName());
    }

    @Test
    public void should_check_parent_certificate_authority_for_commands_that_affect_child_and_parent() {
        HostedCertificateAuthority parent = mock(HostedCertificateAuthority.class, RETURNS_DEEP_STUBS);
        // Make the CA its own parent for this test, easy hack
        when(certificateAuthority.getParent()).thenReturn(parent);
        when(parent.getId()).thenReturn(99L);

        subject.handle(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE), new CommandStatus());

        verify(entityManager, times(1)).find(HostedCertificateAuthority.class, 99L);
    }
}
