package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.server.api.commands.MigrateMemberCertificateAuthorityToIntermediateParentCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.util.DBComponent;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.security.PublicKey;
import java.util.Objects;

@Handler
@Slf4j
public class MigrateMemberCertificateAuthorityToIntermediateParentCommandHandler extends AbstractCertificateAuthorityCommandHandler<MigrateMemberCertificateAuthorityToIntermediateParentCommand> {

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;
    private final DBComponent dbComponent;

    @Inject
    MigrateMemberCertificateAuthorityToIntermediateParentCommandHandler(@NonNull CertificateAuthorityRepository certificateAuthorityRepository,
                                                                        @NonNull ResourceCertificateRepository resourceCertificateRepository,
                                                                        @NonNull ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga,
                                                                        @NonNull DBComponent dbComponent) {
        super(certificateAuthorityRepository);
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
        this.dbComponent = dbComponent;
    }

    @Override
    public Class<MigrateMemberCertificateAuthorityToIntermediateParentCommand> commandType() {
        return MigrateMemberCertificateAuthorityToIntermediateParentCommand.class;
    }

    @Override
    public void handle(@NonNull MigrateMemberCertificateAuthorityToIntermediateParentCommand command, @NonNull CommandStatus commandStatus) {
        ChildCertificateAuthority memberCA = lookupCa(command.getCertificateAuthorityId());
        // First lock the new parent, then the old parent to ensure the correct locking order (child-before-parent) is observed.
        Long productionCaId = dbComponent.lockCertificateAuthorityForSharing(command.getNewParentId());
        if (productionCaId == null) {
            throw new EntityNotFoundException(String.format("intermediate CA %d not found", command.getNewParentId()));
        }
        dbComponent.lockCertificateAuthorityForSharing(productionCaId);

        IntermediateCertificateAuthority newParent = lookupCa(IntermediateCertificateAuthority.class, command.getNewParentId());
        ProductionCertificateAuthority oldParent = lookupCa(ProductionCertificateAuthority.class, memberCA.getParent().getId());
        if (Objects.equals(oldParent.getId(), newParent.getId())) {
            throw new CommandWithoutEffectException("new parent is the same as old parent");
        }
        if (!Objects.equals(oldParent.getId(), newParent.getParent().getId())) {
            throw new IllegalStateException(String.format("old parent %s must be the parent of new parent %s, but was %s", oldParent, newParent, newParent.getParent()));
        }

        for (PublicKey signedPublicKey : memberCA.getSignedPublicKeys()) {
            oldParent.processCertificateRevocationRequest(new CertificateRevocationRequest(signedPublicKey), resourceCertificateRepository);
        }

        memberCA.switchParentTo(newParent);

        childParentCertificateUpdateSaga.execute(memberCA, Integer.MAX_VALUE);
    }
}
