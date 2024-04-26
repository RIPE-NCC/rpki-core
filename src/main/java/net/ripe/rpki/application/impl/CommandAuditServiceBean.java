package net.ripe.rpki.application.impl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAudit;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCreationCommand;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.security.RoleBasedAuthenticationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandAuditServiceBean implements CommandAuditService {

    public static final int MAX_HISTORY_ENTRIES_RETURNED = 2500;

    @PersistenceContext
    private EntityManager entityManager;

    private final RoleBasedAuthenticationStrategy authenticationStrategy;

    @Autowired
    public CommandAuditServiceBean(RoleBasedAuthenticationStrategy authenticationStrategy) {
        this(authenticationStrategy, null);
    }

    CommandAuditServiceBean(RoleBasedAuthenticationStrategy authenticationStrategy, EntityManager entityManager) {
        this.authenticationStrategy = authenticationStrategy;
        this.entityManager = entityManager;
    }

    @Override
    public List<CommandAuditData> findMostRecentCommandsForCa(long caId) {
        List<CommandAudit> commands = entityManager.createQuery(
                "SELECT cd FROM CommandAudit cd WHERE deletedAt IS NULL AND cd.certificateAuthorityId = :caId " +
                    "ORDER BY cd.certificateAuthorityVersion DESC",
                CommandAudit.class
            )
            .setParameter("caId", caId)
            .setMaxResults(MAX_HISTORY_ENTRIES_RETURNED)
            .getResultList();
        return convertToData(commands);
    }

    private List<CommandAuditData> convertToData(List<CommandAudit> commandAuditList) {
        return commandAuditList.stream().map(CommandAudit::toData).toList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CommandAuditData> findCommandsSinceCaVersion(VersionedId caId) {
        Query query = entityManager.createQuery("SELECT cd FROM CommandAudit cd WHERE deletedAt IS NULL AND cd.certificateAuthorityId = :id AND cd.certificateAuthorityVersion > :version ORDER BY cd.certificateAuthorityVersion ASC");
        query.setParameter("id", caId.getId());
        query.setParameter("version", caId.getVersion());
        List<CommandAudit> commands = query.getResultList();
        return convertToData(commands);
    }

    @Override
    @NonNull
    public CommandContext startRecording(CertificateAuthorityCommand command) {
        final String userId = authenticationStrategy.getOriginalUserId().getId().toString();
        VersionedId caVersionedId;
        X500Principal caName;
        UUID caUuid;
        if (command instanceof CertificateAuthorityCreationCommand activationCommand) {
            caVersionedId = activationCommand.getCertificateAuthorityVersionedId();
            caName = activationCommand.getName();
            caUuid = activationCommand.getUuid();
        } else {
            CertificateAuthority ca = entityManager.getReference(CertificateAuthority.class, command.getCertificateAuthorityId());
            caVersionedId = ca.getVersionedId();
            caName = ca.getName();
            caUuid = ca.getUuid();
        }

        CommandAudit commandAudit = new CommandAudit(userId, caVersionedId, caName, caUuid, command);
        if (command.getCommandGroup() == CertificateAuthorityCommandGroup.USER) {
            entityManager.persist(commandAudit);
        }

        return new CommandContext(command, commandAudit);
    }

    @Override
    public void finishRecording(CommandContext context) {
        CommandAudit commandAudit = context.getCommandAudit();
        CertificateAuthorityCommand command = context.getCommand();

        List<String> events = context.getRecordedEvents().stream().map(Object::toString).toList();
        String commandEvents = events.stream().collect(Collectors.joining("\n    ", "\n    ", ""));

        log.info(
            "principal={} caId={} commandType={} commandGroup={} commandSummary={}, events=[{}]",
            commandAudit.getPrincipal(),
            commandAudit.getCertificateAuthorityVersionedId(),
            command.getCommandType(),
            command.getCommandGroup().name(),
            command.getCommandSummary(),
            commandEvents
        );
        if (entityManager.contains(commandAudit) || !events.isEmpty()) {
            commandAudit.setCommandEvents(String.join("\n", events));
            entityManager.persist(commandAudit);
        }
    }

    @Override
    public void deleteCommandsForCa(long caId) {
        Query query = entityManager.createQuery("UPDATE CommandAudit cd SET cd.deletedAt = CURRENT_TIMESTAMP() WHERE cd.certificateAuthorityId = :id");
        query.setParameter("id", caId);
        query.executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Long> findMentionsInSummary(String item) {
        Query query = entityManager.createQuery(
                "SELECT ca FROM CommandAudit ca " +
                        "WHERE commandSummary LIKE :itemSpaces " +
                        "OR commandSummary LIKE :itemParens");
        query.setParameter("itemSpaces", "% " + item + " %");
        query.setParameter("itemParens", "%'" + item + "'%");
        List<CommandAudit> commands = query.getResultList();
        return commands.stream().collect(Collectors.toMap(CommandAudit::getCommandType, c -> 1L, Long::sum));
    }
}
