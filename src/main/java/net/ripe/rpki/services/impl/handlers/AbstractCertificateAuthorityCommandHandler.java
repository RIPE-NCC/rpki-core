package net.ripe.rpki.services.impl.handlers;

import lombok.AccessLevel;
import lombok.Getter;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.persistence.EntityNotFoundException;
import java.util.Objects;

public abstract class AbstractCertificateAuthorityCommandHandler<T extends CertificateAuthorityCommand> implements CertificateAuthorityCommandHandler<T> {

    @Getter(AccessLevel.PROTECTED)
    private final CertificateAuthorityRepository certificateAuthorityRepository;

    protected AbstractCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        this.certificateAuthorityRepository = Objects.requireNonNull(certificateAuthorityRepository, "certificateAuthorityRepository is null");
    }

    protected HostedCertificateAuthority lookupHostedCA(Long id) {
        HostedCertificateAuthority result = certificateAuthorityRepository.findHostedCa(id);
        if (result == null) {
            throw new EntityNotFoundException("hosted CA not found: " + id);
        }
        return result;
    }

    protected CertificateAuthority lookupCA(Long id) {
        return certificateAuthorityRepository.get(id);
    }

    public void handle(T command) {
        handle(command, CommandStatus.create());
    }
}
