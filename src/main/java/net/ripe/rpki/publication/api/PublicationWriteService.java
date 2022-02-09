package net.ripe.rpki.publication.api;

import net.ripe.rpki.domain.PublishedObjectData;

import java.io.IOException;
import java.util.List;


public interface PublicationWriteService {
    void writeAll(List<PublishedObjectData> publishedObjects) throws IOException;
}
