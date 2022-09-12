package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthorityTest;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.support.objects.CaName;
import net.ripe.rpki.services.impl.handlers.ChildParentCertificateUpdateSaga;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AllCaCertificateUpdateServiceBeanTest extends CertificationDomainTestCase {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");
    private static final VersionedId PRODUCTION_CA_ID = new VersionedId(1L);

    private AllCaCertificateUpdateServiceBean subject;

    @MockBean
    private ActiveNodeService activeNodeService;

    @MockBean
    private CertificateAuthorityViewService caViewService;

    @MockBean
    private CommandService commandService;

    @MockBean
    private ResourceCache resourceCache;

    @MockBean
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @MockBean
    private ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    private TransactionTemplate transactionTemplate;

    private ProductionCertificateAuthority productionCa;

    @Autowired
    private RepositoryConfiguration repositoryConfiguration;

    private CertificateAuthorityData productionCaMock;

    @Before
    public void setUp() {
        productionCa = ProductionCertificateAuthorityTest.createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        inTx(() -> createCaIfDoesntExist(productionCa));

        transactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        subject = new AllCaCertificateUpdateServiceBean(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()), caViewService, commandService, resourceCache, repositoryConfiguration, transactionTemplate, certificateAuthorityRepository, childParentCertificateUpdateSaga, 1000);

        when(activeNodeService.isActiveNode()).thenReturn(true);
        when(commandService.execute(isA(CertificateAuthorityCommand.class))).thenReturn(CommandStatus.create());

        CertificateAuthorityData allResourcesCaMock = mock(CertificateAuthorityData.class);
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal())).thenReturn(allResourcesCaMock);

        productionCaMock = mock(CertificateAuthorityData.class);
        when(productionCaMock.getVersionedId()).thenReturn(productionCa.getVersionedId());
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal())).thenReturn(productionCaMock);
    }


    @Test
    public void shouldNotCallBackendServiceIfInactive() {
        when(activeNodeService.isActiveNode()).thenReturn(false);

        subject.execute();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldDoNothingIfProductionCaIsMissing() {
        when(activeNodeService.isActiveNode()).thenReturn(true);
        when(caViewService.findCertificateAuthorityByName(repositoryConfiguration.getProductionCaPrincipal())).thenReturn(null);

        subject.execute();

        verifyNoInteractions(commandService);
    }

    @Test
    public void should_dispatch_update_command_to_every_member_ca_that_needs_an_update() {
        CertificateAuthority child1 = mock(CertificateAuthority.class);
        CertificateAuthority child2 = mock(CertificateAuthority.class);
        CaIdentity memberCa1 = new CaIdentity(new VersionedId(10L), CaName.of(new X500Principal("CN=nl.isp")));
        CaIdentity memberCa2 = new CaIdentity(new VersionedId(11L), CaName.of(new X500Principal("CN=gr.isp")));

        when(childParentCertificateUpdateSaga.isUpdateNeeded(any())).thenReturn(true);
        when(productionCaMock.getName()).thenReturn(PRODUCTION_CA_NAME);
        when(caViewService.findAllChildrenIdsForCa(PRODUCTION_CA_NAME)).thenReturn(Arrays.asList(memberCa1, memberCa2));
        when(certificateAuthorityRepository.find(memberCa1.getVersionedId().getId())).thenReturn(child1);
        when(certificateAuthorityRepository.find(memberCa2.getVersionedId().getId())).thenReturn(child2);
        when(child1.getParent()).thenReturn(productionCa);
        when(child2.getParent()).thenReturn(productionCa);

        subject.execute();


        verify(commandService, times(4)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        verify(commandService, times(2)).execute(new UpdateAllIncomingResourceCertificatesCommand(productionCaMock.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa1.getVersionedId(), Integer.MAX_VALUE));
        verify(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(memberCa2.getVersionedId(), Integer.MAX_VALUE));
    }

    @Test
    public void should_not_throw_exception_if_fails() {
        doThrow(new RuntimeException("test")).when(commandService).execute(new UpdateAllIncomingResourceCertificatesCommand(PRODUCTION_CA_ID, Integer.MAX_VALUE));
        subject.execute();
    }

    @Test
    public void shouldSkipCaIfResourceCacheIsEmpty() {
        doThrow(new IllegalStateException("TEST")).when(resourceCache).verifyResourcesArePresent();

        assertThatThrownBy(() -> subject.runService()).isInstanceOf(IllegalStateException.class);

        verify(resourceCache, times(1)).verifyResourcesArePresent();
        verifyNoInteractions(commandService);
    }
}
