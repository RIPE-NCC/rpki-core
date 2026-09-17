package net.ripe.rpki.domain.bgpsec;

import lombok.experimental.UtilityClass;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificateBuilder;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.joda.time.DateTime;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.PublicKey;

import static net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper.DEFAULT_SIGNATURE_PROVIDER;

@UtilityClass
public class BgpSecCertificateFixtures {

    static final Asn DEFAULT_ASN = Asn.parse("AS64496");

    public static BgpSecCertificate createBgpSecCertificateFromCSR(
            KeyPairEntity signingKeyPair,
            PublicKey publicKey,
            Asn asn,
            long serial,
            DateTime notBefore,
            DateTime notAfter,
            CertificateStatus status
    ) {
        IncomingResourceCertificate currentIncomingCert = signingKeyPair.getCurrentIncomingCertificate();

        X509RouterCertificate x509Cert = new X509RouterCertificateBuilder()
                .withKeyUsage(KeyUsage.digitalSignature)
                .withSignatureProvider(DEFAULT_SIGNATURE_PROVIDER)
                .withAsns(new int[]{asn.getValue().intValueExact()})
                .withSerial(BigInteger.valueOf(serial))
                .withSubjectDN(new X500Principal("CN=BGPSec " + serial))
                .withPublicKey(publicKey)
                .withIssuerDN(currentIncomingCert.getSubject())
                .withValidityPeriod(new ValidityPeriod(notBefore, notAfter))
                .withSigningKeyPair(signingKeyPair.getKeyPair())
                .withAuthorityInformationAccess(new ResourceCertificateInformationAccessStrategyBean().aiaForCertificate(currentIncomingCert))
                .withCrlDistributionPoints(signingKeyPair.crlLocationUri())
                .build();

        BgpSecCertificate cert = new BgpSecCertificate(x509Cert, signingKeyPair, asn);
        cert.setStatus(status);
        return cert;
    }

    public static BgpSecCertificate createBgpSecCertificateFromCSR(
            KeyPairEntity signingKeyPair,
            long serial,
            DateTime notBefore,
            DateTime notAfter,
            CertificateStatus status
    ) {
        PublicKey publicKey = KeyPairFactory.bgpSec().generate().getPublic();
        return createBgpSecCertificateFromCSR(signingKeyPair, publicKey, DEFAULT_ASN, serial, notBefore, notAfter, status);
    }

    public static BgpSecCertificate createBgpSecCertificateFromCSR(
            KeyPairEntity signingKeyPair,
            String csr,
            long serial,
            DateTime notBefore,
            DateTime notAfter
    ) {
        return createBgpSecCertificateFromCSR(
                signingKeyPair, Csr.getPublicKey(csr), DEFAULT_ASN, serial, notBefore, notAfter, CertificateStatus.CURRENT);
    }
}
