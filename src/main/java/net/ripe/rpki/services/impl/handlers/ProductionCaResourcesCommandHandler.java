package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.ProductionCaResourcesCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;

@Handler
public class ProductionCaResourcesCommandHandler extends AbstractCertificateAuthorityCommandHandler<ProductionCaResourcesCommand> {

    private final CertificateRequestCreationService certificateRequestCreationService;
    private final KeyPairService keyPairService;

    @Inject
    ProductionCaResourcesCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                        KeyPairService keyPairService,
                                        CertificateRequestCreationService certificateRequestCreationService) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
        this.certificateRequestCreationService = certificateRequestCreationService;
    }

    @Override
    public Class<ProductionCaResourcesCommand> commandType() {
        return ProductionCaResourcesCommand.class;
    }

    @Override
    public void handle(ProductionCaResourcesCommand command, CommandStatus commandStatus) {
        ProductionCertificateAuthority productionCa = (ProductionCertificateAuthority) lookupHostedCA(command.getCertificateAuthorityVersionedId().getId());
        productionCa.processCertifiableResources(command.getResourceClasses(), keyPairService, certificateRequestCreationService);
    }
}
