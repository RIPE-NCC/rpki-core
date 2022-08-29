package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;


@Handler
public class KeyManagementActivatePendingKeysCommandHandler extends AbstractCertificateAuthorityCommandHandler<KeyManagementActivatePendingKeysCommand> {

    @Inject
    public KeyManagementActivatePendingKeysCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository) {
        super(certificateAuthorityRepository);
    }

    @Override
    public Class<KeyManagementActivatePendingKeysCommand> commandType() {
        return KeyManagementActivatePendingKeysCommand.class;
    }

    /**
     *  Step 5  of Key Rollover, see:  http://tools.ietf.org/html/rfc6489#section-2
     * Activate NEW CA Instance which keys are now in pending state.
     */
    @Override
    public void handle(KeyManagementActivatePendingKeysCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityVersionedId().getId());
        if (!ca.activatePendingKeys(command.getMinStagingTime())) {
            throw new CommandWithoutEffectException("No keys to activate for ca: " + ca.getName());
        }
    }
}
