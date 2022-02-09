package net.ripe.rpki.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.net.URI;
import java.util.Arrays;

@Entity
@Table(name = "ta_published_object")
public class TrustAnchorPublishedObject extends GenericPublishedObject {

    @Column(name = "uri", nullable = false)
    private String uri;

    protected TrustAnchorPublishedObject() {}

    public TrustAnchorPublishedObject(URI uri, byte[] content) {
        this.status = PublicationStatus.TO_BE_PUBLISHED;
        this.uri = uri.toString();
        this.content = Arrays.copyOf(content, content.length);
    }

    public URI getUri() {
        return URI.create(uri);
    }
}
