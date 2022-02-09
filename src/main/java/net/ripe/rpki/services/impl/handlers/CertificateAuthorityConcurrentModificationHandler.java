package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.commands.CertificateAuthorityModificationCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityConcurrentModificationException;
import net.ripe.rpki.server.api.services.command.CommandStatus;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Handler(order=20)
public class CertificateAuthorityConcurrentModificationHandler extends AbstractCertificateAuthorityCommandHandler<CertificateAuthorityModificationCommand> {

    public static final String CONCURRENT_MODIFICATION_METRIC = "rpkicore.command.handler.concurrent.modification";

    private final CommandAuditService commandAuditService;
    private final Counter concurrentModificationExceptionCount;
    private final Counter concurrentModificationPassedCount;
    private final Counter concurrentModificationSystemCommandCount;


    @Inject
    public CertificateAuthorityConcurrentModificationHandler(
            CertificateAuthorityRepository certificateAuthorityRepository,
            CommandAuditService commandAuditService,
            MeterRegistry meterRegistry
    ) {
        super(certificateAuthorityRepository);
        this.commandAuditService = commandAuditService;

        concurrentModificationExceptionCount = Counter.builder(CONCURRENT_MODIFICATION_METRIC)
                .description("The number of concurrent modification handler results")
                .tag("status", "exception")
                .register(meterRegistry);
        concurrentModificationPassedCount = Counter.builder(CONCURRENT_MODIFICATION_METRIC)
                .tag("status", "passed")
                .register(meterRegistry);
        concurrentModificationSystemCommandCount = Counter.builder(CONCURRENT_MODIFICATION_METRIC)
                .tag("status", "system-commmand")
                .register(meterRegistry);
    }

    @Override
    public Class<CertificateAuthorityModificationCommand> commandType() {
        return CertificateAuthorityModificationCommand.class;
    }

    @Override
    public void handle(CertificateAuthorityModificationCommand command, CommandStatus commandStatus) {
        VersionedId versionedId = command.getCertificateAuthorityVersionedId();
        CertificateAuthority ca = lookupCA(versionedId.getId());
        assertNoConflictingCommandsExist(command, versionedId, ca);
    }

    private void assertNoConflictingCommandsExist(CertificateAuthorityModificationCommand command, VersionedId versionedId, CertificateAuthority ca) {
        if (command.getCommandGroup() == CertificateAuthorityCommandGroup.SYSTEM) {
            concurrentModificationSystemCommandCount.increment();
            return;
        }
        List<CommandAuditData> commandsSinceCaVersion = commandAuditService.findCommandsSinceCaVersion(versionedId);
        List<CommandAuditData> conflictingCommands = filterConflictingCommands(command, commandsSinceCaVersion);
        if (!conflictingCommands.isEmpty()) {
            concurrentModificationExceptionCount.increment();
            throw new CertificateAuthorityConcurrentModificationException(command, ca.getVersionedId().getVersion(), conflictingCommands);
        }
        concurrentModificationPassedCount.increment();
    }

    private List<CommandAuditData> filterConflictingCommands(CertificateAuthorityModificationCommand attemptedCommand, List<CommandAuditData> commandsSinceCaVersion) {
        if (attemptedCommand.getCommandGroup() != CertificateAuthorityCommandGroup.SYSTEM) {
            return commandsSinceCaVersion.stream()
                    .filter(existingCommand -> conflicts(attemptedCommand, existingCommand))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean conflicts(CertificateAuthorityModificationCommand attempted, CommandAuditData existing) {
        if (existing.getCommandGroup() == CertificateAuthorityCommandGroup.SYSTEM) {
            return false;
        }

        return !(attempted instanceof UpdateRoaConfigurationCommand) || !existing.getCommandType().equals(UpdateRoaAlertIgnoredAnnouncedRoutesCommand.class.getSimpleName());
    }
}
