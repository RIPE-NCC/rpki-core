package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.crl.X509CrlBuilder;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectBuilder;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.x509.*;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTimeUtils;

import javax.persistence.*;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.util.Random;


/**
 * Contains the identity material (certificate, keypair) used by the Production CA for it's BPKI when talking to
 * non-hosted clients.
 */
@Entity
@Table(name = "down_stream_provisioning_communicator")
@SequenceGenerator(name = "seq_identity_material", sequenceName = "seq_all", allocationSize = 1)
public class DownStreamProvisioningCommunicator extends EntitySupport {

    private static final Random SECURE_RANDOM = new SecureRandom();
    private static final int SERIAL_RANDOM_BITS = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_identity_material")
    private Long id;

    @Embedded
    private PersistedKeyPair persistedKeyPair;

    @NotNull
    @Column(name = "encoded_certificate", nullable = false)
    private byte[] encodedIdentityCertificate;

    @NotNull
    @Column(name = "encoded_crl", nullable = false)
    private byte[] encodedIdentityCrl;

    @Override
    public Object getId() {
        return id;
    }

    protected DownStreamProvisioningCommunicator() {
    }

    public DownStreamProvisioningCommunicator(KeyPairEntityKeyInfo keyInfo, KeyPairEntitySignInfo signInfo, X500Principal identityCertificateSubject) {
        this.persistedKeyPair = new PersistedKeyPair(keyInfo, signInfo);

        ProvisioningIdentityCertificate identityCertificate = createProvisioningIdentityCertificate(identityCertificateSubject);
        this.encodedIdentityCertificate = identityCertificate.getEncoded();

        X509Crl crlHelper = createProvisioningIdentityCrl(identityCertificate);
        this.encodedIdentityCrl = crlHelper.getEncoded();
    }

    private X509Crl createProvisioningIdentityCrl(ProvisioningIdentityCertificate identityCertificate) {
        X509CrlBuilder crlBuilder = new X509CrlBuilder();

        crlBuilder.withIssuerDN(identityCertificate.getSubject());
        crlBuilder.withThisUpdateTime(identityCertificate.getValidityPeriod().getNotValidBefore());
        crlBuilder.withNextUpdateTime(identityCertificate.getValidityPeriod().getNotValidAfter());
        crlBuilder.withNumber(BigInteger.ONE);

        try {
            crlBuilder.withAuthorityKeyIdentifier(persistedKeyPair.getPublicKey());
            crlBuilder.withSignatureProvider(persistedKeyPair.getSignatureProvider());
            return crlBuilder.build(persistedKeyPair.getPrivateKey());
        } finally {
            persistedKeyPair.unloadKeyPair();
        }

    }

    private ProvisioningIdentityCertificate createProvisioningIdentityCertificate(X500Principal identityCertificateSubject) {
        ProvisioningIdentityCertificateBuilder builder = new ProvisioningIdentityCertificateBuilder();
        try {
            builder.withSelfSigningKeyPair(persistedKeyPair.getKeyPair());
            builder.withSelfSigningSubject(identityCertificateSubject);
            builder.withSignatureProvider(persistedKeyPair.getSignatureProvider());
            return builder.build();
        } finally {
            persistedKeyPair.unloadKeyPair();
        }
    }

    public final KeyPair getKeyPair() {
        return persistedKeyPair.getKeyPair();
    }

    public ProvisioningIdentityCertificate getProvisioningIdentityCertificate() {
        ProvisioningIdentityCertificateParser parser = new ProvisioningIdentityCertificateParser();
        parser.parse("provisioning-identity-certificate", encodedIdentityCertificate);
        Validate.isTrue(!parser.getValidationResult().hasFailures());
        return parser.getCertificate();
    }

    public X509CRL getProvisioningCrl() {
        X509Crl crlHelper = new X509Crl(encodedIdentityCrl);
        return crlHelper.getCrl();
    }

    public ProvisioningCmsObject createProvisioningCmsResponseObject(KeyPairFactory keyPairFactory, AbstractProvisioningPayload responsePayload) {
        ProvisioningCmsObjectBuilder cmsObjectBuilder = new ProvisioningCmsObjectBuilder();

        cmsObjectBuilder.withPayloadContent(responsePayload);
        cmsObjectBuilder.withCrl(getProvisioningCrl());
        cmsObjectBuilder.withSignatureProvider(persistedKeyPair.getSignatureProvider());

        KeyPair eeKeyPair = keyPairFactory.generate();
        cmsObjectBuilder.withCmsCertificate(createCmsCertificate(eeKeyPair).getCertificate());

        return cmsObjectBuilder.build(eeKeyPair.getPrivate());
    }

    private ProvisioningCmsCertificate createCmsCertificate(KeyPair eeKeyPair) {
        ProvisioningCmsCertificateBuilder builder = new ProvisioningCmsCertificateBuilder();

        builder.withIssuerDN(getProvisioningIdentityCertificate().getSubject());

        BigInteger serial = generateCertificateSerialNumber();

        builder.withSerial(serial);
        builder.withPublicKey(eeKeyPair.getPublic());

        builder.withSubjectDN(new UuidRepositoryObjectNamingStrategy().getCertificateSubject(eeKeyPair.getPublic()));
        try {
            builder.withSigningKeyPair(persistedKeyPair.getKeyPair());
            builder.withSignatureProvider(persistedKeyPair.getSignatureProvider());
            return builder.build();
        } finally {
            persistedKeyPair.unloadKeyPair();
        }
    }

    private BigInteger generateCertificateSerialNumber() {
        BigInteger now = BigInteger.valueOf(DateTimeUtils.currentTimeMillis());
        BigInteger random = new BigInteger(SERIAL_RANDOM_BITS, SECURE_RANDOM);
        return now.shiftLeft(SERIAL_RANDOM_BITS).or(random);
    }

}
