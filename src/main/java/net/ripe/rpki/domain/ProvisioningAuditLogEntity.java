package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayload;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParser;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParserException;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "provisioning_audit_log")
@SequenceGenerator(name = "seq_provisioning_audit_log", sequenceName = "seq_all", allocationSize = 1)
public class ProvisioningAuditLogEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_provisioning_audit_log")
    private Long id;

    @SuppressWarnings("unused")
    @Column(name = "non_hosted_ca_uuid", nullable = false)
    @Getter
    private UUID nonHostedCaUUID;

    @SuppressWarnings("unused")
    @Column(name = "request_message_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter
    private PayloadMessageType requestMessageType;

    @Column(name = "provisioning_cms_object", nullable = false)
    @Getter
    private byte[] provisioningCmsObject;

    @Column(nullable = false)
    @Getter
    private String principal;

    @Column(nullable = false)
    @Getter
    private String summary;

    @Column(name = "executiontime", nullable = false)
    @Getter
    private Timestamp executionTime;

    @Column(name = "entry_uuid", nullable = false)
    @Getter
    private UUID entryUuid;

    protected ProvisioningAuditLogEntity() {
        this.entryUuid = UUID.randomUUID();
    }

    public ProvisioningAuditLogEntity(ProvisioningCmsObject provisioningCmsObject, String principal, UUID memberUUID) {
        this.executionTime = new Timestamp(DateTimeUtils.currentTimeMillis());

        this.principal = principal;
        this.provisioningCmsObject = provisioningCmsObject.getEncoded();

        this.nonHostedCaUUID = memberUUID;
        this.requestMessageType = provisioningCmsObject.getPayload().getType();

        this.summary = buildCommandSummary(provisioningCmsObject);
        this.entryUuid = UUID.randomUUID();
    }

    @Override
    public Long getId() {
        return id;
    }

    public ProvisioningAuditData toData() {
        return new ProvisioningAuditData(new DateTime(executionTime.getTime(), DateTimeZone.UTC), principal, summary);
    }

    public String buildCommandSummary(ProvisioningCmsObject provisioningCmsObject) {
        final AbstractProvisioningPayload payload = provisioningCmsObject.getPayload();

        StringBuilder stringBuilder = new StringBuilder();
        buldPayloadSummary(payload, stringBuilder);

        return stringBuilder.toString();
    }

    private void buldPayloadSummary(AbstractProvisioningPayload payload, StringBuilder stringBuilder) {

        switch (payload.getType()) {
            case list:
                buildResourceClassListQueryPayloadSummary((ResourceClassListQueryPayload) payload, stringBuilder);
                break;
            case list_response:
                buildResourceClassListResponsePayloadSummary((ResourceClassListResponsePayload) payload, stringBuilder);
                break;
            case issue:
                buildCertificateIssuanceRequestPayloadSummary((CertificateIssuanceRequestPayload) payload, stringBuilder);
                break;
            case issue_response:
                buildCertificateIssuanceResponsePayloadSummary((CertificateIssuanceResponsePayload) payload, stringBuilder);
                break;
            case revoke:
                buildCertificateRevocationRequestPayloadSummary((CertificateRevocationRequestPayload) payload, stringBuilder);
                break;
            case revoke_response:
                buildCertificateRevocationResponsePayloadSummary((CertificateRevocationResponsePayload) payload, stringBuilder);
                break;
            case error_response:
                buildRequestNotPerformedResponsePayloadSummary((RequestNotPerformedResponsePayload) payload, stringBuilder);
                break;
            default:
                stringBuilder.append("unrecognized_request_type");
                break;
        }
    }

    private void buildResourceClassListQueryPayloadSummary(ResourceClassListQueryPayload payload, StringBuilder stringBuilder) {
        if (payload == null) throw new NullPointerException();
        stringBuilder.append("querying for certifiable resources");
    }

    private void buildResourceClassListResponsePayloadSummary(ResourceClassListResponsePayload payload, StringBuilder stringBuilder) {
        List<ResourceClassListResponseClassElement> classElements = payload.getClassElements();
        stringBuilder.append("responding with certifiable resource classes: ");

        int count = 0;
        for (ResourceClassListResponseClassElement classElement : classElements) {
            ImmutableResourceSet.Builder builder = new ImmutableResourceSet.Builder();
            if (classElement.getResourceSetIpv4() != null)
                builder.addAll(classElement.getResourceSetIpv4());
            if (classElement.getResourceSetIpv6() != null)
                builder.addAll(classElement.getResourceSetIpv6());
            ImmutableResourceSet resources = builder.build();

            if (count++ > 0) stringBuilder.append(", ");
            stringBuilder.append(classElement.getClassName()).append(" (").append(resources).append(")");
        }

    }

    private void buildCertificateIssuanceRequestPayloadSummary(CertificateIssuanceRequestPayload payload, StringBuilder stringBuilder) {
        stringBuilder.append("requesting certificate with");

        ImmutableResourceSet.Builder builder = new ImmutableResourceSet.Builder();
        if (payload.getRequestElement().getAllocatedIpv4() != null)
            builder.addAll(payload.getRequestElement().getAllocatedIpv4());
        if (payload.getRequestElement().getAllocatedIpv6() != null)
            builder.addAll(payload.getRequestElement().getAllocatedIpv6());
        ImmutableResourceSet resources = builder.build();

        if (resources.isEmpty()) {
            stringBuilder.append(" all resources");
        } else {
            stringBuilder.append(" ").append(resources);
        }

        stringBuilder.append(" for resource class ").append(payload.getRequestElement().getClassName());

        try {
            RpkiCaCertificateRequestParser rpkiCaCertificateRequestParser = new RpkiCaCertificateRequestParser(payload.getRequestElement().getCertificateRequest());
            String encodedKeyIdentifier = KeyPairUtil.getEncodedKeyIdentifier(rpkiCaCertificateRequestParser.getPublicKey());
            stringBuilder.append(" for key hash: ").append(encodedKeyIdentifier);
        } catch (RpkiCaCertificateRequestParserException e) {
            //Should never get here.
            stringBuilder.append(" for key hash: -- ");
        }
    }

    private void buildCertificateIssuanceResponsePayloadSummary(CertificateIssuanceResponsePayload payload, StringBuilder stringBuilder) {
        stringBuilder.append("signed certificate with resources: ");

        stringBuilder.append(" ").append(payload.getClassElement().getCertificateElement().getCertificate().getResources());

        String encodedKeyIdentifier = KeyPairUtil.getEncodedKeyIdentifier(payload.getClassElement().getCertificateElement().getCertificate().getPublicKey());
        stringBuilder.append(" for key hash: ").append(encodedKeyIdentifier);
        stringBuilder.append(", and validity time: ").append(payload.getClassElement().getCertificateElement().getCertificate().getValidityPeriod());
    }

    private void buildCertificateRevocationRequestPayloadSummary(CertificateRevocationRequestPayload payload, StringBuilder stringBuilder) {
        stringBuilder.append("requesting revocation of all certificates for key hash: ").append(payload.getKeyElement().getPublicKeyHash());
    }

    private void buildCertificateRevocationResponsePayloadSummary(CertificateRevocationResponsePayload payload, StringBuilder stringBuilder) {
        stringBuilder.append("revoked certificates for key hash: ").append(payload.getKeyElement().getPublicKeyHash());
    }

    private void buildRequestNotPerformedResponsePayloadSummary(RequestNotPerformedResponsePayload payload, StringBuilder stringBuilder) {
        stringBuilder
                .append("fail to process request from ").append(payload.getRecipient())
                .append(" to ").append(payload.getSender())
                .append(": ").append(payload.getStatus())
                .append(" - ").append(payload.getDescription());
    }
}
