package net.ripe.rpki.server.api.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

@EqualsAndHashCode
@ToString(exclude = "keystoreName")
@Getter
public class KeyPairData implements Serializable {
    private final Long keyPairId;
    private final String name;

    // seems to include the keystore content -> ignore in toString.
    private final String keystoreName;
    private final KeyPairStatus status;
    private final DateTime creationDate;
    private final Map<KeyPairStatus, DateTime> statusChangedTimestamps;
    private final String crlFilename;
    private final String manifestFilename;
    private final URI certificateRepositoryLocation;
    private final boolean isDbProvider;

    public KeyPairData(Long keyPairId, String keystoreName, KeyPairStatus status, DateTime creationDate,
                       Map<KeyPairStatus, DateTime> statusChangedTimestamps, URI certificateRepositoryLocation,
                       String crlFilename, String manifestFilename, boolean isDbProvider) {
        this.keyPairId = keyPairId;
        this.name = "KEY-" + keyPairId; // API backwards compatibility?
        this.keystoreName = keystoreName;
        this.status = status;
        this.creationDate = creationDate;
        this.statusChangedTimestamps = statusChangedTimestamps;
        this.certificateRepositoryLocation = certificateRepositoryLocation;
        this.crlFilename = crlFilename;
        this.manifestFilename = manifestFilename;
        this.isDbProvider = isDbProvider;
    }
}
