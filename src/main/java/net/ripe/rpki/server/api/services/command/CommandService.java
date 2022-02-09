package net.ripe.rpki.server.api.services.command;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;

/**
 * Send commands here. Use getNextId to get unique ids for use in commands.
 */
public interface CommandService {

    /**
     * Get a new globally unique id to use for entities
     */
    VersionedId getNextId();

    /**
     * <p>
     * Let the back-end handle a {@link CertificateAuthorityCommand}.
     * </p>
     * <p>
     * Certain command handlers may throw different runtime exceptions.
     * A {@link CertificateAuthorityConcurrentModificationException} is thrown in case the certificate authority has been
     * modified since the {@link VersionedId} mentioned in the command. This is indicative of concurrency issues.
     * </p>
     *
     * @throws CertificateAuthorityConcurrentModificationException
     * @throws OfflineResponseProcessorException
     * @throws CertificateAuthorityNameNotUniqueException
     */
    CommandStatus execute(CertificateAuthorityCommand command) throws CertificateAuthorityConcurrentModificationException, OfflineResponseProcessorException, CertificateAuthorityNameNotUniqueException;

}
