package net.ripe.rpki.domain;

import lombok.Value;

import java.net.URI;
import java.sql.Timestamp;

@Value
public class PublishedObjectData {
    Timestamp createdAt;

    URI uri;

    byte[] content;

    public PublishedObjectData(Timestamp createdAt, URI uri, byte[] content) {
        this.createdAt = createdAt;
        this.uri = uri;
        this.content = content;
    }
}
