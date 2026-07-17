package net.ripe.rpki.domain.bgpsec;

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@UtilityClass
public class Csr {
    public static PublicKey getPublicKey(byte[] csr) {
        try {
            var r = new JcaPKCS10CertificationRequest(csr);
            validateBgpsecRequirements(r);
            return r.getPublicKey();
        } catch (Exception e) {
            throw new IllegalCsrException(e);
        }
    }

    private static void validateBgpsecRequirements(JcaPKCS10CertificationRequest csr) {
        try {
            var verifierProvider = new JcaContentVerifierProviderBuilder().build(csr.getPublicKey());
            if (!csr.isSignatureValid(verifierProvider)) {
                throw new IllegalCsrException("CSR signature is invalid");
            }
        } catch (IllegalCsrException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalCsrException("CSR signature verification failed", e);
        }

        // https://datatracker.ietf.org/doc/html/rfc8208#section-3.1
        var curveOid = csr.getSubjectPublicKeyInfo().getAlgorithm().getParameters();
        if (!X9ObjectIdentifiers.prime256v1.equals(curveOid)) {
            throw new IllegalCsrException("Public key must use P-256 curve per RFC 8208");
        }

        // https://datatracker.ietf.org/doc/html/rfc8208#section-2
        var sigAlgOid = csr.getSignatureAlgorithm().getAlgorithm();
        if (!X9ObjectIdentifiers.ecdsa_with_SHA256.equals(sigAlgOid)) {
            throw new IllegalCsrException("Signature must use ECDSA with SHA-256 per RFC 8208");
        }
    }

    public static PublicKey getPublicKey(String csr) {
        return getPublicKey(Base64.getMimeDecoder().decode(csr));
    }

    public static void validate(String csr) {
        if (getPublicKey(csr) == null) {
            throw new IllegalArgumentException("CSR " + csr + " is not valid.");
        }
    }

    public static String getKeyIdentifier(String csr) {
        try {
            return getKeyIdentifier(Base64.getMimeDecoder().decode(csr));
        } catch (Exception e) {
            throw new IllegalCsrException(e);
        }
    }

    public static String getKeyIdentifier(byte[] csr) {
        try {
            var spki = new JcaPKCS10CertificationRequest(csr).getSubjectPublicKeyInfo();
            @SuppressWarnings("java:S4790")
            var sha1 = MessageDigest.getInstance("SHA-1");
            byte[] ki = sha1.digest(spki.getPublicKeyData().getBytes());
            return HexFormat.of().formatHex(ki).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate key identifier", e);
        }
    }
}
