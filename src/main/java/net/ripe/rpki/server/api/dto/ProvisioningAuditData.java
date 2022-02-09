package net.ripe.rpki.server.api.dto;

import org.joda.time.DateTime;

public class ProvisioningAuditData extends CertificateAuthorityHistoryItem {

    public ProvisioningAuditData(DateTime executionTime, String principal, String summary) {
        super(executionTime, principal, summary);

    }
}
