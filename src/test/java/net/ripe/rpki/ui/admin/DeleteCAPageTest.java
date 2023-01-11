package net.ripe.rpki.ui.admin;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.ui.application.CertificationWicketTestCase;
import org.apache.wicket.PageParameters;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteCAPageTest extends CertificationWicketTestCase {

    private CertificateAuthorityData hostedCA;

    @Before
    public void setUp() {
        hostedCA = new ManagedCertificateAuthorityData(new VersionedId(12, 1),
            new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
            ImmutableResourceSet.empty(), Collections.emptyList());
    }

    @Test
    public void shouldSimplyRender() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class);

        tester.assertRenderedPage(DeleteCAPage.class);
    }

    @Test
    public void shouldShowTheRegIdFormIfNoCAGiven() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class);

        tester.assertVisible("caNameForm");
        tester.assertInvisible("deleteForm");
    }

    @Test
    public void shouldShowEmptyRegIdFieldOnTheForm() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class);

        assertEquals("", tester.newFormTester("caNameForm").getTextComponentValue("caName"));
    }

    @Test
    public void shouldRegIdBeRequired() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class);

        tester.assertRequired("caNameForm:caName");
    }

    @Test
    public void shouldSubmitRegIdForm() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());
        when(caViewService.findCertificateAuthorityIdByName(new X500Principal("CN=zz.example"))).thenReturn(12L);
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class);
        FormTester formTester = tester.newFormTester("caNameForm");
        formTester.setValue("caName", "CN=zz.example");
        formTester.submit("findButton");

        tester.assertRenderedPage(DeleteCAPage.class);
        verify(commandService, never()).execute(isA(DeleteCertificateAuthorityCommand.class));
    }

    @Test
    public void shouldShowErrorIfThereIsNoCaForTheGivenRegId() {
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());
        when(caViewService.findCertificateAuthorityIdByName(new X500Principal("CN=zz.example"))).thenReturn(null);

        tester.startPage(DeleteCAPage.class);
        FormTester formTester = tester.newFormTester("caNameForm");
        formTester.setValue("caName", "CN=zz.example");
        formTester.submit("findButton");

        tester.assertRenderedPage(DeleteCAPage.class);
        tester.assertErrorMessages(new String[]{"Certificate Authority for this CA name does not exist."});
        verify(commandService, never()).execute(isA(DeleteCertificateAuthorityCommand.class));
    }

    @Test
    public void shouldShowHistoryForRegId() {
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class, pageParams());

        tester.assertVisible("commandListPanel");
    }

    @Test
    public void shouldHideTheRegIdFormIfCASpecified() {
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class, pageParams());

        tester.assertVisible("deleteForm");
        tester.assertInvisible("caNameForm");
    }

    @Test
    public void shouldSubmitTheDeleteFormAndDeleteHostedCA() {
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());
        when(commandService.execute(isA(DeleteCertificateAuthorityCommand.class))).thenReturn(CommandStatus.create());

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("deleteButton");

        tester.assertInfoMessages(new String[]{"Deleted CA " + "CN=zz.example"});
        verify(commandService).execute(isA(DeleteCertificateAuthorityCommand.class));
    }

    @Test
    public void shouldShowErrorMessageIfCADeletionFails() {
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Some error")).when(commandService).execute(isA(DeleteCertificateAuthorityCommand.class));

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("deleteButton");

        tester.assertErrorMessages(new String[]{"Some error"});
        verify(commandService).execute(isA(DeleteCertificateAuthorityCommand.class));
    }

    @Test
    public void shouldGoBackWithoutSubmittingTheDeleteForm() {
        when(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).thenReturn(hostedCA);
        when(caViewService.findMostRecentCommandsForCa(12L)).thenReturn(Collections.emptyList());
        when(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).thenReturn(Collections.emptyList());

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("backButton");

        verify(commandService, never()).execute(isA(DeleteCertificateAuthorityCommand.class));
    }

    private PageParameters pageParams() {
        PageParameters parameters = new PageParameters();
        parameters.add("caName", "CN=zz.example");
        return parameters;
    }
}
