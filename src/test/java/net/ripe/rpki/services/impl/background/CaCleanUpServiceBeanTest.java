package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CaCleanUpServiceBeanTest {

    private CaCleanUpServiceBean service;

    @Mock
    private ActiveNodeService activeNodeService;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private CommandService commandService;

    @Mock
    private RoaViewService roaViewService;

    @Before
    public void setUp() {
        service = new CaCleanUpServiceBean(activeNodeService, certificateAuthorityRepository,
            commandService, roaViewService, new SimpleMeterRegistry(), true);
    }

    @Test
    public void should_delete_old_cas_without_keypairs() throws Exception {
        service.runService();
        verify(certificateAuthorityRepository).getCasWithoutKeyPairsOlderThenOneYear();
    }

}
