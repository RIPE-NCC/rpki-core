package net.ripe.rpki.ui.admin;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.DeleteNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.ui.application.CertificationWicketTestCase;
import org.apache.wicket.PageParameters;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.UUID;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;

public class DeleteCAPageTest extends CertificationWicketTestCase {

    private CertificateAuthorityData hostedCA;
    private CertificateAuthorityData nonHostedCA;

    @Before
    public void setUp() {
        hostedCA = new ManagedCertificateAuthorityData(new VersionedId(12, 1),
            new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
            new IpResourceSet(), Collections.emptyList());

        nonHostedCA = new NonHostedCertificateAuthorityData(new VersionedId(12, 1),
            new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L,null,
            new IpResourceSet(), Collections.emptySet());
    }

    @Test
    public void shouldSimplyRender() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        tester.assertRenderedPage(DeleteCAPage.class);

        verifyMocks();
    }

    @Test
    public void shouldShowTheRegIdFormIfNoCAGiven() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        tester.assertVisible("caNameForm");
        tester.assertInvisible("deleteForm");

        verifyMocks();
    }

    @Test
    public void shouldShowEmptyRegIdFieldOnTheForm() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        assertEquals("", tester.newFormTester("caNameForm").getTextComponentValue("caName"));

        verifyMocks();
    }

    @Test
    public void shouldRegIdBeRequired() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        tester.assertRequired("caNameForm:caName");

        verifyMocks();
    }

    @Test
    public void shouldSubmitRegIdForm() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        expect(caViewService.findCertificateAuthorityIdByName(new X500Principal("CN=zz.example"))).andReturn(12L);
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        FormTester formTester = tester.newFormTester("caNameForm");
        formTester.setValue("caName", "CN=zz.example");
        formTester.submit("findButton");

        tester.assertRenderedPage(DeleteCAPage.class);

        verifyMocks();
    }

    @Test
    public void shouldShowErrorIfThereIsNoCaForTheGivenRegId() {
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        expect(caViewService.findCertificateAuthorityIdByName(new X500Principal("CN=zz.example"))).andReturn(null);
        replayMocks();

        tester.startPage(DeleteCAPage.class);
        FormTester formTester = tester.newFormTester("caNameForm");
        formTester.setValue("caName", "CN=zz.example");
        formTester.submit("findButton");

        tester.assertRenderedPage(DeleteCAPage.class);
        tester.assertErrorMessages(new String[]{"Certificate Authority for this CA name does not exist."});

        verifyMocks();
    }

    @Test
    public void shouldShowHistoryForRegId() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());
        tester.assertVisible("commandListPanel");

        verifyMocks();
    }

    @Test
    public void shouldHideTheRegIdFormIfCASpecified() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());

        tester.assertVisible("deleteForm");
        tester.assertInvisible("caNameForm");

        verifyMocks();
    }

    @Test
    public void shouldSubmitTheDeleteFormAndDeleteHostedCA() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        expect(roaService.getRoaConfiguration(12L)).andReturn(new RoaConfigurationData(Collections.emptyList()));
        expect(commandService.execute(isA(DeleteCertificateAuthorityCommand.class))).andReturn(CommandStatus.create());
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("deleteButton");

        tester.assertInfoMessages(new String[]{"Deleted CA " + "CN=zz.example"});

        verifyMocks();
    }

    @Test
    public void shouldSubmitTheDeleteFormAndDeleteNonhostedCA() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(nonHostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        expect(commandService.execute(isA(DeleteNonHostedCertificateAuthorityCommand.class))).andReturn(CommandStatus.create());
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("deleteButton");

        tester.assertInfoMessages(new String[]{"Deleted non hosted CA " + "CN=zz.example"});

        verifyMocks();
    }

    @Test
    public void shouldShowErrorMessageIfCADeletionFails() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        expect(roaService.getRoaConfiguration(12L)).andReturn(new RoaConfigurationData(Collections.emptyList()));
        commandService.execute(isA(DeleteCertificateAuthorityCommand.class));
        expectLastCall().andThrow(new RuntimeException("Some error"));
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("deleteButton");

        tester.assertErrorMessages(new String[]{"Some error"});

        verifyMocks();
    }

    @Test
    public void shouldGoBackWithoutSubmittingTheDeleteForm() {
        expect(caViewService.findCertificateAuthorityByName(new X500Principal("CN=zz.example"))).andReturn(hostedCA);
        expect(caViewService.findMostRecentCommandsForCa(12L)).andReturn(Collections.emptyList());
        // Parent page defaults to prodution CA ID - caused verifyMocks() to fail after EasyMock was updated.
        expect(caViewService.findMostRecentCommandsForCa(PRODUCTION_CA_ID)).andReturn(Collections.emptyList());
        replayMocks();

        tester.startPage(DeleteCAPage.class, pageParams());
        FormTester formTester = tester.newFormTester("deleteForm");
        formTester.submit("backButton");

        verifyMocks();
    }

    private PageParameters pageParams() {
        PageParameters parameters = new PageParameters();
        parameters.add("caName", "CN=zz.example");
        return parameters;
    }
}
