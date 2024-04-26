package net.ripe.rpki.domain;

import com.google.common.hash.HashCode;
import lombok.*;

import java.time.Instant;
import java.util.Date;

@NoArgsConstructor
@Data
public class PublishedObjectEntry {
    private Instant updatedAt;
    private PublicationStatus status;

    private String uri;

    /**
     * <b>hex string</b> (not: bytes) of sha256.
     */
    private String sha256;

    /**
     * Constructor that maps the SQL types to the entity
     */
    public PublishedObjectEntry(Instant updatedAt, String status, String uri, byte[] sha256) {
        this.updatedAt = updatedAt;
        this.status = PublicationStatus.valueOf(status);
        this.uri = uri;
        this.sha256 = HashCode.fromBytes(sha256).toString();
    }
}
