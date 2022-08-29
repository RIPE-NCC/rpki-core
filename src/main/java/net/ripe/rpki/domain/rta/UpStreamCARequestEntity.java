package net.ripe.rpki.domain.rta;

import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.xml.XStreamXmlSerializer;
import net.ripe.rpki.commons.xml.XStreamXmlSerializerBuilder;
import net.ripe.rpki.domain.ManagedCertificateAuthority;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Collections;


@Entity
@Table(name = "upstream_request")
@SequenceGenerator(name = "seq_upstream_request", sequenceName = "seq_all", allocationSize = 1)
public class UpStreamCARequestEntity {

    @SuppressWarnings("unused")
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_upstream_request")
    private Long id;

    @Column(name = "upstream_request_xml", nullable = false)
    private String upStreamCARequest;

    @SuppressWarnings("unused")
    // Used for hibernate mapping
    @OneToOne(targetEntity = ManagedCertificateAuthority.class)
    @JoinColumn(name = "requesting_ca_id", nullable = false, unique = true)
    private ManagedCertificateAuthority certificateAuthority;

    protected UpStreamCARequestEntity() {
    }

    public UpStreamCARequestEntity(ManagedCertificateAuthority certificateAuthority, TrustAnchorRequest upStreamCARequest) {
        this.certificateAuthority = certificateAuthority;
        this.upStreamCARequest = getRequestSerialiser().serialize(upStreamCARequest);
    }

    public TrustAnchorRequest getUpStreamCARequest() {
        return getRequestSerialiser().deserialize(migrateUpstreamCARequest(upStreamCARequest));
    }

    private XStreamXmlSerializer<TrustAnchorRequest> getRequestSerialiser() {
        return XStreamXmlSerializerBuilder.newForgivingXmlSerializerBuilder(TrustAnchorRequest.class)
                .withAllowedType(TrustAnchorRequest.class)
                .withAllowedTypeHierarchy(TaRequest.class)
                .withAllowedTypeHierarchy(Collections.emptyList().getClass())
                .build();

    }

    /**
     * Apply migrations to (old) stored upstream CA requests.
     *
     * As the database contains the original CA request, some migrations must be
     * applied to old requests, to allow parsing the requests into the domain
     * structure of RPKI Commons.
     */
    private String migrateUpstreamCARequest(String request) {
        return request.replaceAll(
            "<(/?)net\\.ripe\\.rpki\\.offline\\.requests\\.([A-z0-9]+)>",
            "<$1net.ripe.rpki.commons.ta.domain.request.$2>"
        );
    }
}
