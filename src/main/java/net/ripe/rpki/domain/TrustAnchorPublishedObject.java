package net.ripe.rpki.domain;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.joda.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.net.URI;

@NoArgsConstructor
@Entity
@Table(name = "ta_published_object")
public class TrustAnchorPublishedObject extends GenericPublishedObject {
    @Column(name = "uri", nullable = false)
    @NonNull
    private String uri = "";

    public TrustAnchorPublishedObject(@NonNull URI uri, byte[] content, Instant createdAt) {
        super(content, createdAt);
        this.uri = uri.toString();
    }

    public @NonNull URI getUri() {
        return URI.create(uri);
    }
}
