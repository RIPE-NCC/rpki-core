package net.ripe.rpki.services.impl.handlers;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;

public abstract class AbstractCertificateAuthorityCommandHandler<T extends CertificateAuthorityCommand> implements CertificateAuthorityCommandHandler<T> {

    @Getter(AccessLevel.PROTECTED)
    private final CertificateAuthorityRepository certificateAuthorityRepository;

    protected AbstractCertificateAuthorityCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        this.certificateAuthorityRepository = Objects.requireNonNull(certificateAuthorityRepository, "certificateAuthorityRepository is null");
    }

    @NonNull
    protected <U extends CertificateAuthority> U lookupCa(Class<U> type, Long id) {
        U result = certificateAuthorityRepository.find(type, id);
        if (result == null) {
            throw new EntityNotFoundException(type.getSimpleName() + " not found: " + id);
        }
        return result;
    }

    @NonNull
    protected ManagedCertificateAuthority lookupManagedCa(Long id) {
        ManagedCertificateAuthority result = certificateAuthorityRepository.findManagedCa(id);
        if (result == null) {
            throw new EntityNotFoundException("managed CA not found: " + id);
        }
        return result;
    }

    @NonNull
    protected NonHostedCertificateAuthority lookupNonHostedCa(Long id) {
        NonHostedCertificateAuthority result = certificateAuthorityRepository.findNonHostedCa(id);
        if (result == null) {
            throw new EntityNotFoundException("non-hosted CA not found: " + id);
        }
        return result;
    }

    protected CertificateAuthority lookupCa(Long id) {
        return certificateAuthorityRepository.get(id);
    }

    public void handle(T command) {
        handle(command, CommandStatus.create());
    }
}
