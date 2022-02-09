package net.ripe.rpki.server.api.dto;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.net.URI;
import java.util.Collections;


public class KeyPairDataTest {

    private static final long KEY_PAIR_ID = 45;

    public static KeyPairData currentKeyPairDataWithName(String name) {
        return new KeyPairData(KEY_PAIR_ID, name, "keystore-" + name, KeyPairStatus.CURRENT, new DateTime(DateTimeZone.UTC), Collections.emptyMap(), URI.create("rsync://rpki.invalid/repository"), "CRL", "MANIFEST", false);
    }

    public static KeyPairData keyPairDataWithNameAndStatusAndCreationDate(String name, KeyPairStatus status, DateTime creationDate) {
        return new KeyPairData(KEY_PAIR_ID, name, "keystore-" + name, status, creationDate, Collections.singletonMap(status, creationDate), URI.create("rsync://rpki.invalid/repository"), "CRL", "MANIFEST", false);
    }
}
