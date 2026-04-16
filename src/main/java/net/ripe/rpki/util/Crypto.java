package net.ripe.rpki.util;

import lombok.experimental.UtilityClass;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;

@UtilityClass
public class Crypto {
    private static MessageDigest makeSha(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize " + algorithm + " MessageDigest", e);
        }
    }

    public static String getKeyIdentifier(byte[] key) {
        var hash = makeSha("SHA-1").digest(key);
        return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
    }

    public static String sha256(byte[] bytes) {
        var hash = makeSha("SHA-256").digest(bytes);
        return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
    }

    public static String sha256(Stream<byte[]> data) {
        var sha256 = makeSha("SHA-256");
        data.forEachOrdered(sha256::update);
        return Base64.getEncoder().encodeToString(sha256.digest());
    }
}
