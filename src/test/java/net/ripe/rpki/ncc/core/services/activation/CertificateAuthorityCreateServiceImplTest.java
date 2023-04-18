package net.ripe.rpki.ncc.core.services.activation;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

public class CertificateAuthorityCreateServiceImplTest {

    private static final X500Principal PRODUCTION_CA_NAME = new X500Principal("CN=RIPE NCC Resources,O=RIPE NCC,C=NL");

    private static final long PRODUCTION_CA_ID = 1L;

    private static final ManagedCertificateAuthorityData INTERMEDIATE_CA = new ManagedCertificateAuthorityData(
        new VersionedId(2L), new X500Principal("CN=intermediate"), UUID.randomUUID(), PRODUCTION_CA_ID, CertificateAuthorityType.INTERMEDIATE, ALL_PRIVATE_USE_RESOURCES, Collections.emptyList()
    );

    private static final X500Principal MEMBER_CA = new X500Principal("CN=nl.bluelight");
    private static final ImmutableResourceSet MEMBER_RESOURCES = ImmutableResourceSet.parse("10.0.0.0/16");
    private static final VersionedId MEMBER_CA_ID = new VersionedId(3L);

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

        subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME);

        var commandArgumentCaptor = ArgumentCaptor.forClass(ActivateHostedCertificateAuthorityCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());
        ActivateHostedCertificateAuthorityCommand command = commandArgumentCaptor.getValue();
        assertThat(command.getName()).isEqualTo(MEMBER_CA);
        assertThat(command.getResources()).isEqualTo(MEMBER_RESOURCES);
        assertThat(command.getParentId()).isEqualTo(PRODUCTION_CA_ID);
    }

    @Test
    public void should_provision_hosted_ca_with_smallest_intermediate_ca_as_parent() {
        when(caViewService.findSmallestIntermediateCa(PRODUCTION_CA_NAME)).thenReturn(Optional.of(INTERMEDIATE_CA));
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME);

        var commandArgumentCaptor = ArgumentCaptor.forClass(ActivateHostedCertificateAuthorityCommand.class);
        verify(commandService).execute(commandArgumentCaptor.capture());

        ActivateHostedCertificateAuthorityCommand command = commandArgumentCaptor.getValue();
        assertThat(command.getParentId()).isEqualTo(INTERMEDIATE_CA.getId());

        verify(caViewService, never()).findCertificateAuthorityIdByTypeAndName(any(), any());
    }

    @Test(expected = CertificateAuthorityNameNotUniqueException.class)
    public void shouldThrowCertificateAuthorityNameNotUniqueExceptionIfCaAlreadyExists() {
        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_ID);
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        doThrow(NameNotUniqueException.class).when(commandService).execute(isA(ActivateHostedCertificateAuthorityCommand.class));

        subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME);
    }

    @Test
    public void shouldProvisioningFailIfProductionCaDoesNotExist() {
        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(null);

        assertThatThrownBy(() -> subject.provisionMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME))
            .isInstanceOfSatisfying(IllegalArgumentException.class, e -> {
                assertThat(e).hasMessage("Production Certificate Authority 'CN=RIPE NCC Resources,O=RIPE NCC,C=NL' not found");
            });
    }

    @Test
    public void shouldProvisionNonHostedMember() throws IOException {
        ProvisioningIdentityCertificate identityCertificate = loadCertificate();

        when(caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, PRODUCTION_CA_NAME)).thenReturn(PRODUCTION_CA_ID);
        when(commandService.getNextId()).thenReturn(MEMBER_CA_ID);

        ArgumentCaptor<ActivateNonHostedCertificateAuthorityCommand> commandArgument =
            ArgumentCaptor.forClass(ActivateNonHostedCertificateAuthorityCommand.class);

        subject.provisionNonHostedMember(MEMBER_CA, MEMBER_RESOURCES, PRODUCTION_CA_NAME, identityCertificate);

        verify(commandService).execute(commandArgument.capture());

        final ActivateNonHostedCertificateAuthorityCommand command = commandArgument.getValue();
        assertThat(command.getCertificateAuthorityId()).isEqualTo(MEMBER_CA_ID.getId());
        assertThat(command.getName()).isEqualTo(MEMBER_CA);
        assertThat(command.getResources()).isEqualTo(MEMBER_RESOURCES);
        assertThat(command.getIdentityCertificate()).isEqualTo(identityCertificate);
        assertThat(command.getParentId()).isEqualTo(PRODUCTION_CA_ID);
    }

    private ProvisioningIdentityCertificate loadCertificate() throws IOException {
        var bytes = Files.readAllBytes(Path.of("src/test/resources/cert/idcert-1.cer"));
        ProvisioningIdentityCertificateParser certificateParser = new ProvisioningIdentityCertificateParser();
        certificateParser.parse("/tmp", bytes);
        return certificateParser.getCertificate();
    }
}
