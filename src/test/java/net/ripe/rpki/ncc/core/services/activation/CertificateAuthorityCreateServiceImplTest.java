package net.ripe.rpki.ncc.core.services.activation;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CertificateAuthorityCreateServiceImplTest {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");

    private static final long PRODUCTION_CA_ID = 1L;

    private static final X500Principal MEMBER_CA = new X500Principal("CN=nl.bluelight");
    private static final VersionedId MEMBER_CA_ID = new VersionedId(2L);
    private static final IpResourceSet MEMBER_RESOURCES = IpResourceSet.parse("10.0.0.0/16");

    private CertificateAuthorityCreateServiceImpl subject;

    private ResourceLookupService resourceLookupService;
    private CertificateAuthorityViewService caViewService;
    private CommandService commandService;

    @Before
    public void setUp() {
        resourceLookupService = mock(ResourceLookupService.class);
        caViewService = mock(CertificateAuthorityViewService.class);
        commandService = mock(CommandService.class);
        subject = new CertificateAuthorityCreateServiceImpl(resourceLookupService, caViewService, commandService, PRODUCTION_CA_NAME.getName());
    }

    @Test
    public void shouldProvisionMember() {
        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_ID);
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        ActivateCustomerCertificateAuthorityCommand command = new ActivateCustomerCertificateAuthorityCommand(MEMBER_CA_ID, MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_ID);

        subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME);

        verify(commandService).execute(command);
    }

    @Test(expected = CertificateAuthorityNameNotUniqueException.class)
    public void shouldThrowCertificateAuthorityNameNotUniqueExceptionIfCaAlreadyExists() {
        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_ID);
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        ActivateCustomerCertificateAuthorityCommand command = new ActivateCustomerCertificateAuthorityCommand(MEMBER_CA_ID, MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_ID);
        doThrow(NameNotUniqueException.class).when(commandService).execute(command);

        subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME);
    }

    @Test
    public void shouldProvisioningFailIfProductionCaDoesNotExist() {

        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(null);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME));
        assertEquals("Production Certificate Authority 'CN=RIPE NCC Resources,O=RIPE NCC,C=NL' not found", illegalArgumentException.getMessage());
    }

    @Test
    public void shouldProvisionNonHostedMember() throws IOException {
        ProvisioningIdentityCertificate identityCertificate = loadCertificate();

        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_ID);
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        ActivateNonHostedCertificateAuthorityCommand command = new ActivateNonHostedCertificateAuthorityCommand(MEMBER_CA_ID, MEMBER_CA, MEMBER_RESOURCES, identityCertificate, PRODUCTION_CA_ID);

        subject.provisionNonHostedMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME, identityCertificate);

        verify(commandService).execute(command);
    }

    private ProvisioningIdentityCertificate loadCertificate() throws IOException {
        FileInputStream in = null;
        ProvisioningIdentityCertificate identityCertificate;

        try {

            in = new FileInputStream("src/test/resources/cert/idcert-1.cer");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            int c;

            while ((c = in.read()) != -1) {
                bos.write(c);
            }

            byte[] bytes = bos.toByteArray();

            ProvisioningIdentityCertificateParser certificateParser = new ProvisioningIdentityCertificateParser();
            certificateParser.parse("/tmp", bytes);

            identityCertificate = certificateParser.getCertificate();
        } finally {
            if (in != null)
                in.close();
        }
        return identityCertificate;
    }
}
