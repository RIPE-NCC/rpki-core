package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import jakarta.inject.Inject;

@Handler
public class ActivateHostedCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateHostedCertificateAuthorityCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;
    private final ManifestPublicationService manifestPublicationService;
    @Inject
    ActivateHostedCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                     ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga,
                                                     ManifestPublicationService manifestPublicationService) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
        this.manifestPublicationService = manifestPublicationService;
    }

    @Override
    public Class<ActivateHostedCertificateAuthorityCommand> commandType() {
        return ActivateHostedCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull ActivateHostedCertificateAuthorityCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority parentCa = lookupManagedCa(command.getParentId());
        HostedCertificateAuthority memberCa = createMemberCA(command, parentCa);
        childParentCertificateUpdateSaga.execute(memberCa, Integer.MAX_VALUE);

        // Publish the member CA manifest and CRL. This can be safely done since the parent has not published yet
        // so there is no reference to this CA's RPKI object repository and therefore no chance of invalid objects.
        //
        // Once the parent CA's objects are published this CA will become visible to validators.
        manifestPublicationService.publishRpkiObjectsIfNeeded(memberCa);
    }

    private HostedCertificateAuthority createMemberCA(ActivateHostedCertificateAuthorityCommand command, ManagedCertificateAuthority parentCa) {
        HostedCertificateAuthority ca = new HostedCertificateAuthority(command.getCertificateAuthorityId(),
                command.getName(), command.getUuid(), parentCa);
        getCertificateAuthorityRepository().add(ca);
        return ca;
    }

}
