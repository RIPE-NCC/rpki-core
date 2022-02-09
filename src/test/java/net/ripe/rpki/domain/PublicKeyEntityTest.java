package net.ripe.rpki.domain;

import org.junit.Test;

import java.security.PublicKey;

import static org.junit.Assert.assertEquals;


public class PublicKeyEntityTest {

    @Test
    public void shouldStorePublicKey() {
        KeyPairEntity testKeyPair = TestObjects.createTestKeyPair();
        PublicKey publicKey = testKeyPair.getPublicKey();
        PublicKeyEntity remoteKeyPairEntity = new PublicKeyEntity(publicKey);

        PublicKey actualStoredPublicKey = remoteKeyPairEntity.getPublicKey();

        assertEquals(publicKey, actualStoredPublicKey);
    }

    @Test
    public void shouldEncodeKeyIdentifierForRemotePublicKey() {
        KeyPairEntity testKeyPair = TestObjects.createTestKeyPair();
        PublicKey publicKey = testKeyPair.getPublicKey();
        PublicKeyEntity remoteKeyPairEntity = new PublicKeyEntity(publicKey);

        assertEquals(testKeyPair.getEncodedKeyIdentifier(), remoteKeyPairEntity.getEncodedKeyIdentifier());
    }
}
