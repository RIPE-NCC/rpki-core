package net.ripe.rpki.domain.manifest;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;

public record Sha256(byte[] bytes) {

    public Sha256 {
        Validate.notNull(bytes, "bytes is null");
        Validate.isTrue(bytes.length == 32, "input must be 32 bytes long");
    }

    private static final HashFunction HASH_FUNCTION = Hashing.sha256();

    public static Sha256 hash(byte[] source) {
        return new Sha256(HASH_FUNCTION.hashBytes(source).asBytes());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Sha256(byte[] bytes1) && Arrays.equals(bytes, bytes1));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        var s = new StringBuilder();
        for (byte aByte : bytes) {
            s.append(String.format("%02x", aByte));
        }
        return s.toString();
    }

    public boolean sameAs(byte[] other) {
        return Arrays.equals(bytes, other);
    }
}
