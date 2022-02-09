package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.GenerateOfflineCARepublishRequestCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;
import java.util.Collections;

@Handler
public class GenerateOfflineCARepublishRequestCommandHandler extends AbstractCertificateAuthorityCommandHandler<GenerateOfflineCARepublishRequestCommand> {

    private final CertificateRequestCreationService requestCreationService;

    @Inject
    public GenerateOfflineCARepublishRequestCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                           CertificateRequestCreationService requestCreationService) {
        super(certificateAuthorityRepository);
        this.requestCreationService = requestCreationService;
    }

    @Override
    public Class<GenerateOfflineCARepublishRequestCommand> commandType() {
        return GenerateOfflineCARepublishRequestCommand.class;
    }

    @Override
    public void handle(GenerateOfflineCARepublishRequestCommand command, CommandStatus commandStatus) {
        HostedCertificateAuthority ca = lookupHostedCA(command.getCertificateAuthorityVersionedId().getId());
        Validate.isTrue(ca.isAllResourcesCa(), "Only All Resources CA can request Offline CA to republish");

        TrustAnchorRequest trustAnchorRequest = requestCreationService.createTrustAnchorRequest(Collections.emptyList());

        UpStreamCARequestEntity upStreamCARequest = new UpStreamCARequestEntity(ca, trustAnchorRequest);

        ca.setUpStreamCARequestEntity(upStreamCARequest);
    }
}
