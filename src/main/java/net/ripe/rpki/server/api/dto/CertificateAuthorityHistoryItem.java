package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import org.joda.time.DateTime;

import java.io.Serializable;

public abstract class CertificateAuthorityHistoryItem extends ValueObjectSupport implements Serializable {

    private final DateTime executionTime;
    private final String principal;
    private final String commandSummary;

    protected CertificateAuthorityHistoryItem(DateTime executionTime, String principal, String commandSummary) {
        this.executionTime = executionTime;
        this.principal = principal;
        this.commandSummary = commandSummary;
    }

    public DateTime getExecutionTime() {
        return executionTime;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getSummary() {
        return commandSummary;
    }

}
