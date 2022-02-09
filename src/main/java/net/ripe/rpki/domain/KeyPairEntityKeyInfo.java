package net.ripe.rpki.domain;

import java.security.KeyPair;

/**
 * Just a little helper to construct this KeyPairEntity a little more comfortably TODO: make this
 * embedded?
 */
public class KeyPairEntityKeyInfo {

    private final String name;

    private final KeyPair keyPair;

    public KeyPairEntityKeyInfo(String name, KeyPair keyPair) {
        this.name = name;
        this.keyPair = keyPair;
    }

    public String getName() {
        return name;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }
}
