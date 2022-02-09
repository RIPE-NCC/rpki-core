package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.util.DBComponent;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;

@Handler
@Slf4j
public class UpdateAllIncomingResourceCertificatesCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateAllIncomingResourceCertificatesCommand> {

    private final KeyPairService keyPairService;
    private final ResourceLookupService resourceLookupService;
    private final KeyPairDeletionService keyPairArchingService;
    private final CertificateRequestCreationService certificateRequestCreationService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final DBComponent dbComponent;

    @Inject
    UpdateAllIncomingResourceCertificatesCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                        KeyPairService keyPairService,
                                                        ResourceLookupService resourceLookupService,
                                                        KeyPairDeletionService keyPairArchingService,
                                                        CertificateRequestCreationService certificateRequestCreationService,
                                                        PublishedObjectRepository publishedObjectRepository,
                                                        ResourceCertificateRepository resourceCertificateRepository,
                                                        DBComponent dbComponent) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
        this.resourceLookupService = resourceLookupService;
        this.keyPairArchingService = keyPairArchingService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.dbComponent = dbComponent;
    }

    @Override
    public Class<UpdateAllIncomingResourceCertificatesCommand> commandType() {
        return UpdateAllIncomingResourceCertificatesCommand.class;
    }

    @Override
    public void handle(UpdateAllIncomingResourceCertificatesCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);
        final boolean hasEffect;
        final CertificateAuthority certificateAuthority = lookupCA(command.getCertificateAuthorityVersionedId().getId());
        if (certificateAuthority.getParent() == null) {
            log.error("cannot update incoming resource certificate for CAs without parent {}", certificateAuthority);
            hasEffect = false;
        } else {
            hasEffect = new ChildParentCertificateUpdateSaga(keyPairArchingService, certificateRequestCreationService,
                publishedObjectRepository, resourceCertificateRepository, dbComponent)
                .execute(certificateAuthority.getParent(), certificateAuthority, resourceLookupService, keyPairService);
        }

        if (!hasEffect) {
            throw new CommandWithoutEffectException(command);
        }
    }
}
