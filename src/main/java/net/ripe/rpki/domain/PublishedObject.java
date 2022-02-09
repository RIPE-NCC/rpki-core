package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.ValidityPeriod;

import javax.persistence.*;
import java.net.URI;
import java.util.Arrays;


/* for PublishedObjectentry, needs to be on an @Entity for Hibernate to detect it */
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
    private KeyPairEntity issuingKeyPair;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "included_in_manifest", nullable = false)
    private boolean includedInManifest;

    @Column(name = "directory", nullable = false)
    private String directory;

    // Nullable until all data has been migrated to fill in these columns
    @Embedded
    @AttributeOverrides( {
        @AttributeOverride(name = "notValidBefore", column = @Column(name = "validity_not_before", nullable = false)),
        @AttributeOverride(name = "notValidAfter", column = @Column(name = "validity_not_after", nullable = false))
    })
    private EmbeddedValidityPeriod validityPeriod;

    protected PublishedObject() {
    }

    public PublishedObject(KeyPairEntity issuingKeyPair, String filename, byte[] content, boolean includedInManifest,
                           URI publicationDirectory, ValidityPeriod validityPeriod) {
        this.status = PublicationStatus.TO_BE_PUBLISHED;
        this.issuingKeyPair = issuingKeyPair;
        this.filename = filename;
        this.content = Arrays.copyOf(content, content.length);
        this.includedInManifest = includedInManifest;
        String dir = publicationDirectory.toString();
        this.directory = (dir.endsWith("/")) ? dir : dir + "/";
        this.validityPeriod = new EmbeddedValidityPeriod(validityPeriod);
    }

    public KeyPairEntity getIssuingKeyPair() {
        return issuingKeyPair;
    }

    public String getFilename() {
        return filename;
    }

    public URI getUri() {
        return URI.create(directory).resolve(filename);
    }

    public String getDirectory() {
        return directory;
    }

    public boolean isIncludedInManifest() {
        return includedInManifest;
    }

    /**
     * @return true if this object will be published or is published.
     */
    public boolean isPublished() {
        return status.isPublished();
    }

    public ValidityPeriod getValidityPeriod() {
        return validityPeriod == null ? null : validityPeriod.toValidityPeriod();
    }

    public void setValidityPeriod(ValidityPeriod validityPeriod) {
        this.validityPeriod = validityPeriod == null ? null : new EmbeddedValidityPeriod(validityPeriod);
    }
}
