package net.ripe.rpki.domain;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import org.apache.commons.lang3.Validate;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import java.net.URI;
import java.util.Objects;


/* for PublishedObjectEntry, needs to be on an @Entity for Hibernate to detect it */
@SqlResultSetMapping(name="PublishedObjectEntryResult", classes = {
        @ConstructorResult(targetClass = PublishedObjectEntry.class, columns = {
                @ColumnResult(name="updated_at"),
                @ColumnResult(name="status"),
                @ColumnResult(name="uri"),
                @ColumnResult(name="sha256_content")
        })
})
@Entity
@Table(name = "published_object")
public class PublishedObject extends GenericPublishedObject {

    @ManyToOne(optional = true)
    @JoinColumn(name = "issuing_key_pair_id", nullable = true)
    @Getter
    private KeyPairEntity issuingKeyPair;

    @Column(name = "filename", nullable = false)
    @Getter
    @NonNull
    private String filename = "";

    /**
     * Indicates if this object should be included as an entry in the manifest. Currently only the manifest CMS object
     * itself is _not_ included in the manifest, but there might be future object types that are not intended to be
     * part of the manifest.
     */
    @Column(name = "included_in_manifest", nullable = false)
    @Getter
    private boolean includedInManifest;

    @Column(name = "directory", nullable = false)
    @Getter
    @NonNull
    private String directory = "";

    @ManyToOne
    @Getter
    private ManifestEntity containingManifest;

    @Embedded
    @AttributeOverride(name = "notValidBefore", column = @Column(name = "validity_not_before", nullable = false))
    @AttributeOverride(name = "notValidAfter", column = @Column(name = "validity_not_after", nullable = false))
    @NonNull
    private EmbeddedValidityPeriod validityPeriod = new EmbeddedValidityPeriod();

    protected PublishedObject() {
    }

    public PublishedObject(
            @NonNull KeyPairEntity issuingKeyPair,
            @NonNull String filename,
            byte[] content,
            boolean includedInManifest,
            @NonNull URI publicationDirectory,
            @NonNull ValidityPeriod validityPeriod,
            @NonNull DateTime createdAt
    ) {
        super(content, createdAt.toInstant());
        this.issuingKeyPair = issuingKeyPair;
        this.filename = filename;
        this.includedInManifest = includedInManifest;
        String dir = publicationDirectory.toString();
        this.directory = (dir.endsWith("/")) ? dir : dir + "/";
        this.validityPeriod = new EmbeddedValidityPeriod(validityPeriod);
    }

    /**
     * Construct a PublishedObject with <emph>implicit</emph> createdAt from the validity period.
     *
     * <emph>Do not use for CMS signed objects or CRLs</emph>
     */
    public PublishedObject(
        @NonNull KeyPairEntity issuingKeyPair,
        @NonNull String filename,
        byte[] content,
        boolean includedInManifest,
        @NonNull URI publicationDirectory,
        @NonNull ValidityPeriod validityPeriod
    ) {
        this(issuingKeyPair, filename, content, includedInManifest, publicationDirectory, validityPeriod, validityPeriod.getNotValidBefore());
    }

    @NonNull
    public URI getUri() {
        return URI.create(directory).resolve(filename);
    }

    /**
     * @return true if this object will be published or is published.
     */
    public boolean isPublished() {
        return status.isPublished();
    }

    @NonNull
    public ValidityPeriod getValidityPeriod() {
        return validityPeriod.toValidityPeriod();
    }

    public void setValidityPeriod(@NonNull ValidityPeriod validityPeriod) {
        this.validityPeriod = new EmbeddedValidityPeriod(validityPeriod);
    }

    public void setContainingManifest(ManifestEntity containingManifest) {
        Validate.isTrue(isIncludedInManifest(), "only published objects that must be included in the manifest can have a containing manifest");
        Validate.isTrue(
            containingManifest == null || Objects.equals(this.getIssuingKeyPair(), containingManifest.getKeyPair()),
            "published object and manifest must be issued by same key pair"
        );
        this.containingManifest = containingManifest;
    }
}
