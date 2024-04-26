package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.offline.ra.service.TrustAnchorResponseProcessor;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import jakarta.inject.Inject;

@Handler
public class ProcessTrustAnchorResponseCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProcessTrustAnchorResponseCommand> {

    private final TrustAnchorResponseProcessor trustAnchorResponseProcessor;

    @Inject
    public ProcessTrustAnchorResponseCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                    TrustAnchorResponseProcessor trustAnchorResponseProcessor
    ) {
        super(certificateAuthorityRepository);
        this.trustAnchorResponseProcessor = trustAnchorResponseProcessor;
    }

    @Override
    public Class<ProcessTrustAnchorResponseCommand> commandType() {
        return ProcessTrustAnchorResponseCommand.class;
    }

    @Override
    public void handle(ProcessTrustAnchorResponseCommand command, CommandStatus commandStatus) {
        trustAnchorResponseProcessor.process(command.getOfflineResponse());
    }
}
