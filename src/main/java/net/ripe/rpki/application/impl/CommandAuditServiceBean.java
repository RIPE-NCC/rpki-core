package net.ripe.rpki.application.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.audit.CommandAudit;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.security.RoleBasedAuthenticationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandAuditServiceBean implements CommandAuditService {

    private static final int MAX_MOST_RECENT_COMMANDS_COUNT = 30;

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

    @SuppressWarnings("unchecked")
    @Override
    public List<CommandAuditData> findMostRecentUserCommandsForCa(long caId) {
        final Query query = entityManager.createQuery(
                "SELECT cd FROM CommandAudit cd WHERE deletedAt IS NULL AND cd.certificateAuthorityId = :caId " +
                        "AND cd.commandGroup = :commandGroup ORDER BY cd.certificateAuthorityVersion DESC");

        query.setParameter("caId", caId);
        query.setParameter("commandGroup", CertificateAuthorityCommandGroup.USER.toString());
        query.setMaxResults(MAX_MOST_RECENT_COMMANDS_COUNT);
        List<CommandAudit> commands = query.getResultList();
        return convertToData(commands);
    }

    private List<CommandAuditData> convertToData(List<CommandAudit> commandAuditList) {
        return commandAuditList.stream().map(CommandAudit::toData).collect(Collectors.toList());
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
    public void record(CertificateAuthorityCommand command, VersionedId caId) {
        final String userId = authenticationStrategy.getOriginalUserId().getId().toString();

        log.info(
            "principal={} caId={} commandType={} commandGroup={} commandSummary={}",
            userId,
            caId,
            command.getCommandType(),
            command.getCommandGroup().name(),
            command.getCommandSummary()
        );
        if (command.getCommandGroup() == CertificateAuthorityCommandGroup.USER) {
            CommandAudit commandAudit = new CommandAudit(userId, command, caId);
            entityManager.persist(commandAudit);
        }
    }

    @Override
    public void deleteCommandsForCa(long caId) {
        Query query = entityManager.createQuery("UPDATE CommandAudit cd SET cd.deletedAt = CURRENT_TIMESTAMP() WHERE cd.certificateAuthorityId = :id");
        query.setParameter("id", caId);
        query.executeUpdate();
    }
}