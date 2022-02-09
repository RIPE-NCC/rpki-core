package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.security.auth.x500.X500Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LockCertificateAuthorityHandlerTest {

    private EntityManager entityManager;
    private LockCertificateAuthorityHandler subject;

    @Before
    public void setUp() {
        entityManager = mock(EntityManager.class);
        subject = new LockCertificateAuthorityHandler(entityManager);
    }

    @Test
    public void should_lock_ca_for_any_CertificateAuthorityCommand() {
        subject.handle(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(123, 1)), CommandStatus.create());

        verify(entityManager).find(CertificateAuthority.class, 123L, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    public void should_lock_parent_ca_for_ActivateCustomerCertificateAuthorityCommand() {
        subject.handle(new ActivateCustomerCertificateAuthorityCommand(
            new VersionedId(123, 0),
            new X500Principal("CN=test"),
            IpResourceSet.ALL_PRIVATE_USE_RESOURCES,
            456
        ), CommandStatus.create());

        verify(entityManager).find(CertificateAuthority.class, 456L, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    public void should_lock_parent_ca_for_ActivateNonHostedCertificateAuthorityCommand() {
        subject.handle(new ActivateNonHostedCertificateAuthorityCommand(
            new VersionedId(123, 0),
            new X500Principal("CN=test"),
            IpResourceSet.ALL_PRIVATE_USE_RESOURCES,
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            999
        ), CommandStatus.create());

        verify(entityManager).find(CertificateAuthority.class, 999L, LockModeType.PESSIMISTIC_WRITE);
    }
}
