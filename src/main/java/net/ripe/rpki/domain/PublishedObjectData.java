package net.ripe.rpki.domain;

import lombok.Value;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;

@Value
public class PublishedObjectData {
    Instant createdAt;

    URI uri;

    byte[] content;

    public PublishedObjectData(Instant createdAt, URI uri, byte[] content) {
        this.createdAt = createdAt;
        this.uri = uri;
        this.content = content;
    }
}
