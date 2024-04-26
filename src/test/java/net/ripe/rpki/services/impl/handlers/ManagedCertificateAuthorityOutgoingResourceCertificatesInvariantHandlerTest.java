package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityInvariantViolationException;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.persistence.EntityManager;
import java.util.Arrays;

import static net.ripe.rpki.domain.TestObjects.CA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ManagedCertificateAuthorityOutgoingResourceCertificatesInvariantHandlerTest {

    private ManagedCertificateAuthorityOutgoingResourceCertificatesInvariantHandler subject;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private ManagedCertificateAuthority certificateAuthority;

    private KeyPairEntity oldKeyPair;
    private KeyPairEntity currentKeyPair;

    @Before
    public void setUp() {
        subject = new ManagedCertificateAuthorityOutgoingResourceCertificatesInvariantHandler(new SimpleMeterRegistry(), entityManager, resourceCertificateRepository);
        when(entityManager.find(ManagedCertificateAuthority.class, CA_ID)).thenReturn(certificateAuthority);

        oldKeyPair = mock(KeyPairEntity.class);
        when(oldKeyPair.isPublishable()).thenReturn(true);
        when(oldKeyPair.getCertifiedResources()).thenReturn(ImmutableResourceSet.parse("10.0.0.0/8"));
        currentKeyPair = mock(KeyPairEntity.class);
        when(currentKeyPair.isPublishable()).thenReturn(true);
        when(currentKeyPair.getCertifiedResources()).thenReturn(ImmutableResourceSet.parse("10.0.0.0/8"));
        when(certificateAuthority.getKeyPairs()).thenReturn(Arrays.asList(oldKeyPair, currentKeyPair));

        when(resourceCertificateRepository.findCurrentOutgoingResourceCertificateResources(certificateAuthority.getName())).thenReturn(ImmutableResourceSet.parse("10.0.0.0/8"));
    }

    @Test
    public void should_check_incoming_resource_consistency() {
        // All incoming resource certificates should have the same resources
        when(oldKeyPair.getCertifiedResources()).thenReturn(ImmutableResourceSet.parse("10.0.0.0/8"));
        when(currentKeyPair.getCertifiedResources()).thenReturn(ImmutableResourceSet.parse("172.16.0.0/12"));

        // so an CertificateAuthorityInvariantViolationException is thrown when they do not match
        assertThatThrownBy(
            () -> subject.handleInternal(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE))
        ).isInstanceOfSatisfying(CertificateAuthorityInvariantViolationException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("CA certificateAuthority: not all incoming certificates have the same resources: [10.0.0.0/8, 172.16.0.0/12]");
        });
    }

    @Test
    public void should_check_outgoing_resource_certificate_consistency() {
        // Outgoing child resources should always be contained in incoming resources
        when(resourceCertificateRepository.findCurrentOutgoingResourceCertificateResources(certificateAuthority.getName())).thenReturn(ImmutableResourceSet.parse("192.168.0.0/16"));

        // so an CertificateAuthorityInvariantViolationException is thrown when they do not match
        assertThatThrownBy(
            () -> subject.handleInternal(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE))
        ).isInstanceOfSatisfying(CertificateAuthorityInvariantViolationException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("CA certificateAuthority: with current resources 10.0.0.0/8 does not contain issued resources 192.168.0.0/16");
        });
    }

    @Test
    public void should_check_parent_certificate_authority_for_commands_that_affect_child_and_parent() {
        ManagedCertificateAuthority parent = mock(ManagedCertificateAuthority.class, RETURNS_DEEP_STUBS);
        // Make the CA its own parent for this test, easy hack
        when(certificateAuthority.getParent()).thenReturn(parent);
        when(parent.getId()).thenReturn(99L);

        subject.handle(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(CA_ID), Integer.MAX_VALUE), new CommandStatus());

        verify(entityManager, times(1)).find(ManagedCertificateAuthority.class, 99L);
    }
}
