package net.ripe.rpki.services.impl.handlers;

import lombok.NonNull;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.IntermediateCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.inject.Inject;

@Handler
@ConditionalOnProperty(prefix="intermediate.ca", value="enabled", havingValue = "true")
public class CreateIntermediateCertificateAuthorityCommandHandler extends AbstractCertificateAuthorityCommandHandler<CreateIntermediateCertificateAuthorityCommand> {

    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    @Inject
    public CreateIntermediateCertificateAuthorityCommandHandler(
        CertificateAuthorityRepository certificateAuthorityRepository,
        ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga
    ) {
        super(certificateAuthorityRepository);
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public Class<CreateIntermediateCertificateAuthorityCommand> commandType() {
        return CreateIntermediateCertificateAuthorityCommand.class;
    }

    @Override
    public void handle(@NonNull CreateIntermediateCertificateAuthorityCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority parentCa = lookupManagedCa(command.getParentId());
        ManagedCertificateAuthority intermediateCa = createIntermediateCa(command, parentCa);
        childParentCertificateUpdateSaga.execute(intermediateCa, Integer.MAX_VALUE);
    }

    @NonNull
    private ManagedCertificateAuthority createIntermediateCa(@NonNull CreateIntermediateCertificateAuthorityCommand command, ManagedCertificateAuthority parentCa) {
        ManagedCertificateAuthority intermediateCa = new IntermediateCertificateAuthority(
            command.getCertificateAuthorityId(),
            command.getName(),
            command.getUuid(),
            parentCa
        );
        getCertificateAuthorityRepository().add(intermediateCa);
        return intermediateCa;
    }
}
