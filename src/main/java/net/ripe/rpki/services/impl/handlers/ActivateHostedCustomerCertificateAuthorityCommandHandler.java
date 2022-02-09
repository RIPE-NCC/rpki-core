package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.util.DBComponent;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
// TODO(yg) rename to ActivateCustomerCertificateAuthorityCommandHandler
public class ActivateHostedCustomerCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<ActivateCustomerCertificateAuthorityCommand> {

    private final CertificationConfiguration certificationConfiguration;
    private final KeyPairService keyPairService;
    private final ResourceLookupService resourceLookupService;
    private final KeyPairDeletionService keyPairDeletionService;
    private final CertificateRequestCreationService certificateRequestCreationService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final DBComponent dbComponent;

    @Inject
    ActivateHostedCustomerCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                             CertificationConfiguration certificationConfiguration,
                                                             KeyPairService keyPairService,
                                                             ResourceLookupService resourceLookupService,
                                                             KeyPairDeletionService keyPairDeletionService,
                                                             CertificateRequestCreationService certificateRequestCreationService,
                                                             PublishedObjectRepository publishedObjectRepository,
                                                             ResourceCertificateRepository resourceCertificateRepository,
                                                             DBComponent dbComponent) {
        super(certificateAuthorityRepository);
        this.certificationConfiguration = certificationConfiguration;
        this.keyPairService = keyPairService;
        this.resourceLookupService = resourceLookupService;
        this.keyPairDeletionService = keyPairDeletionService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.dbComponent = dbComponent;
    }

    @Override
    public Class<ActivateCustomerCertificateAuthorityCommand> commandType() {
        return ActivateCustomerCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(ActivateCustomerCertificateAuthorityCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);
        HostedCertificateAuthority productionCa = lookupHostedCA(command.getParentId());
        CustomerCertificateAuthority memberCa = createMemberCA(command, productionCa);

        new ChildParentCertificateUpdateSaga(keyPairDeletionService, certificateRequestCreationService,
                publishedObjectRepository, resourceCertificateRepository, dbComponent)
                .execute(productionCa, memberCa, resourceLookupService, keyPairService);
    }

    private CustomerCertificateAuthority createMemberCA(ActivateCustomerCertificateAuthorityCommand command, HostedCertificateAuthority parentCa) {
        CustomerCertificateAuthority ca = new CustomerCertificateAuthority(command.getCertificateAuthorityVersionedId().getId(),
                command.getName(), parentCa, certificationConfiguration.getMaxSerialIncrement());
        getCertificateAuthorityRepository().add(ca);
        return ca;
    }

}
