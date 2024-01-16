package net.ripe.rpki.domain.aspa;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCms;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCmsParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.services.command.UnparseableRpkiObjectException;
import org.apache.commons.lang.Validate;

import javax.persistence.*;
import java.net.URI;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

@Entity
@Table(name = "aspaentity")
@SequenceGenerator(name = "seq_aspaentity", sequenceName = "seq_all", allocationSize=1)
@NoArgsConstructor
public class AspaEntity extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_aspaentity")
    @Getter
    private Long id;

    @OneToOne(optional = false)
    @Getter
    private OutgoingResourceCertificate certificate;

    @OneToOne(optional = false, cascade = {CascadeType.PERSIST}, fetch = FetchType.LAZY)
    @JoinColumn(name = "published_object_id", nullable = false)
    @Getter
    private PublishedObject publishedObject;

    @Getter
    @Setter
    @Column(name = "profile_version", nullable = false)
    private Long profileVersion;

    @Transient
    private AspaCms cms;

    public AspaEntity(OutgoingResourceCertificate eeCertificate, AspaCms aspaCms, String filename, URI directory, long profileVersion) {
        super();
        Validate.notNull(eeCertificate);
        Validate.notNull(aspaCms);
        this.certificate = eeCertificate;
        this.publishedObject = new PublishedObject(
                eeCertificate.getSigningKeyPair(), filename, aspaCms.getEncoded(), true, directory, aspaCms.getValidityPeriod(), aspaCms.getSigningTime());
        this.profileVersion = profileVersion;
    }

    public static SortedMap<Asn, SortedSet<Asn>> entitiesToMaps(List<AspaEntity> entities) {
        return streamToSortedMap(
            entities.stream(),
            AspaEntity::getCustomerAsn,
            AspaEntity::getProviders
        );
    }

    public AspaCms getAspaCms() {
        if (cms == null) {
            final AspaCmsParser parser = new AspaCmsParser();
            ValidationResult validationResult = ValidationResult.withLocation("asa");
            parser.parse(validationResult, publishedObject.getContent());
            if (!parser.isSuccess()) {
                throw new UnparseableRpkiObjectException(validationResult);
            }
            cms = parser.getAspa();
        }
        return cms;
    }

    public boolean isRevoked() {
        return getCertificate().isRevoked();
    }

    public void revokeAndRemove(AspaEntityRepository repository) {
        getCertificate().revoke();
        publishedObject.withdraw();
        repository.remove(this);
    }

    public boolean isPublished() {
        return publishedObject.isPublished();
    }

    public void withdraw() {
        publishedObject.withdraw();
    }

    public Asn getCustomerAsn() {
        return getAspaCms().getCustomerAsn();
    }

    public SortedSet<Asn> getProviders() {
        return ImmutableSortedSet.copyOf(getAspaCms().getProviderASSet());
    }
}
