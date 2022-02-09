package net.ripe.rpki.rest.pojo;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import org.joda.time.DateTime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class HistoryItem {

    ZonedDateTime time;
    String principal;
    String commandType;
    String commandGroup;
    Long caId;
    String summary;

    public HistoryItem(String humanizedUserPrincipal, CertificateAuthorityHistoryItem input) {
        this.time = toJavaTime(input.getExecutionTime());
        this.principal = humanizedUserPrincipal;
        this.summary = input.getSummary();
        if (input instanceof CommandAuditData) {
            final CommandAuditData commandData = (CommandAuditData) input;
            this.caId = commandData.getCertificateAuthorityId();
            this.commandType = commandData.getCommandType();
            this.commandGroup = commandData.getCommandGroup().toString();
        } else {
            this.caId = null;
            this.commandType = null;
            this.commandGroup = null;
        }
    }

    private ZonedDateTime toJavaTime(DateTime joda) {
        Instant t = Instant.ofEpochMilli(joda.getMillis());
        ZoneId z = ZoneId.of(joda.getZone().getID());
        return ZonedDateTime.ofInstant(t, z);
    }
}
