package net.ripe.rpki.ripencc.provisioning;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import net.ripe.rpki.application.impl.CommandAuditServiceBean;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.domain.ProvisioningAuditLogEntity;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import org.apache.tomcat.util.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@Transactional
class ProvisioningAuditLogServiceBean implements ProvisioningAuditLogService {
    /**
     * <emph>Important:</emph> separate logger for provisioning messages. There tend to be quite big and should not
     * end up in the main logfile. Name needs to match logback configuration (!).
     */
    private static final Logger provisioningLog = LoggerFactory.getLogger("provisioning-logger");

    @PersistenceContext
    protected EntityManager entityManager;

    public ProvisioningAuditLogServiceBean() {
        this(null);
    }

    public ProvisioningAuditLogServiceBean(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("java:S3457")
    @SneakyThrows
    @Override
    public void log(ProvisioningAuditLogEntity entry, byte[] request) {
        // We use structured/json logging: The LogEntry will be added to the json.
        // There is no need to also template it into the log line (this would bloat the log). SonarQube warning is ignored.
        provisioningLog.info("Up-down message", kv("entry", LogEntry.make(entry, request)));
        final PayloadMessageType requestMessageType = entry.getRequestMessageType();
        if (requestMessageType != PayloadMessageType.list &&
            requestMessageType != PayloadMessageType.list_response) {
            entityManager.persist(entry);
        }
    }

    @Override
    public List<ProvisioningAuditData> findRecentMessagesForCA(UUID caUUID) {
        final TypedQuery<ProvisioningAuditLogEntity> query = entityManager.createQuery(
            "select pal from ProvisioningAuditLogEntity pal " +
                "where pal.nonHostedCaUUID = :caUUID", ProvisioningAuditLogEntity.class);
        query.setParameter("caUUID", caUUID);
        query.setMaxResults(CommandAuditServiceBean.MAX_HISTORY_ENTRIES_RETURNED);
        List<ProvisioningAuditLogEntity> messages = query.getResultList();
        return messages.stream()
            .map(ProvisioningAuditLogEntity::toData)
            .collect(Collectors.toList());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class LogEntry {
        private final String requestMessageType;
        private final String provisioningCmsObject;
        private final String principal;
        private final String nonHostedCaUUID;
        private final String summary;
        private final String entryUuid;
        private final String executionTime;
        private final String request;

        public static LogEntry make(ProvisioningAuditLogEntity entry, byte[] request) {
            final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
            final DateTime utcDate = new DateTime(entry.getExecutionTime().getTime(), DateTimeZone.UTC);
            return new LogEntry(
                entry.getRequestMessageType().toString(),
                Base64.encodeBase64String(entry.getProvisioningCmsObject()),
                entry.getPrincipal(),
                Objects.toString(entry.getNonHostedCaUUID(), null),
                entry.getSummary(),
                Objects.toString(entry.getEntryUuid(), null),
                dateFormat.print(utcDate),
                // since request is a DER binary, encode it as base64 as well
                Base64.encodeBase64String(request));
        }
    }

}
