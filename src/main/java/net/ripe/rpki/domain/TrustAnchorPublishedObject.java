package net.ripe.rpki.domain;

import lombok.NonNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.net.URI;

@Entity
@Table(name = "ta_published_object")
public class TrustAnchorPublishedObject extends GenericPublishedObject {

    @Column(name = "uri", nullable = false)
    @NonNull
    private String uri = "";

    protected TrustAnchorPublishedObject() {
    }

    public TrustAnchorPublishedObject(@NonNull URI uri, byte[] content) {
        super(content);
        this.uri = uri.toString();
    }

    public @NonNull URI getUri() {
        return URI.create(uri);
    }
}
