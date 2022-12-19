package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
public class AllResourcesCaResourcesCommandHandler extends AbstractCertificateAuthorityCommandHandler<AllResourcesCaResourcesCommand> {

    private final CertificateRequestCreationService certificateRequestCreationService;
    private final KeyPairService keyPairService;

    @Inject
    AllResourcesCaResourcesCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                          KeyPairService keyPairService,
                                          CertificateRequestCreationService certificateRequestCreationService) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
        this.certificateRequestCreationService = certificateRequestCreationService;
    }

    @Override
    public Class<AllResourcesCaResourcesCommand> commandType() {
        return AllResourcesCaResourcesCommand.class;
    }

    @Override
    public void handle(AllResourcesCaResourcesCommand command, CommandStatus commandStatus) {
        final AllResourcesCertificateAuthority allResourcesCa = (AllResourcesCertificateAuthority) lookupManagedCa(command.getCertificateAuthorityId());
        allResourcesCa.processCertifiableResources(keyPairService, certificateRequestCreationService);
    }
}
