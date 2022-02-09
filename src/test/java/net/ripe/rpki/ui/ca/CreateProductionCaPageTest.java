package net.ripe.rpki.ui.ca;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.ui.admin.UpstreamCaManagementPage;
import net.ripe.rpki.ui.application.AbstractCertificationWicketTest;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;


public class CreateProductionCaPageTest extends AbstractCertificationWicketTest {

    @Test
    public void shouldCreateRootCa() {
        given(repositoryConfiguration.getProductionCaPrincipal()).willReturn(PRODUCTION_CA_PRINCIPAL);
        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(null);
        given(commandService.getNextId()).willReturn(new VersionedId(PRODUCTION_CA_ID));

        tester.startPage(CreateProductionCaPage.class);
        tester.assertRenderedPage(CreateProductionCaPage.class);

        given(caViewService.findCertificateAuthorityByName(PRODUCTION_CA_PRINCIPAL)).willReturn(PRODUCTION_CA_DATA);

        tester.newFormTester("createCertificateAuthorityForm").submit();

        ArgumentCaptor<CreateRootCertificateAuthorityCommand> argumentCaptor = ArgumentCaptor.forClass(CreateRootCertificateAuthorityCommand.class);
        verify(commandService).execute(argumentCaptor.capture());
        verify(activeNodeService).activateCurrentNode();
        tester.assertRenderedPage(UpstreamCaManagementPage.class);
    }
}
