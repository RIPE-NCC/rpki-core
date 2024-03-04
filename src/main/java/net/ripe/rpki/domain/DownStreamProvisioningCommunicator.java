package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.crl.X509CrlBuilder;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectBuilder;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.x509.*;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang.Validate;

import javax.persistence.*;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509CRL;


/**
 * Contains the identity material (certificate, keypair) used by the Production CA for it's BPKI when talking to
 * non-hosted clients.
 */
@Entity
@Table(name = "down_stream_provisioning_communicator")
@SequenceGenerator(name = "seq_identity_material", sequenceName = "seq_all", allocationSize = 1)
public class DownStreamProvisioningCommunicator extends EntitySupport {

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

    public DownStreamProvisioningCommunicator(KeyPair keyPair, KeyPairEntitySignInfo signInfo, X500Principal identityCertificateSubject) {
        this.persistedKeyPair = new PersistedKeyPair(keyPair, signInfo);

        ProvisioningIdentityCertificate identityCertificate = createProvisioningIdentityCertificate(identityCertificateSubject);
        this.encodedIdentityCertificate = identityCertificate.getEncoded();

        X509Crl crlHelper = createProvisioningIdentityCrl(identityCertificate);
        this.encodedIdentityCrl = crlHelper.getEncoded();
    }

    private X509Crl createProvisioningIdentityCrl(ProvisioningIdentityCertificate identityCertificate) {
        X509CrlBuilder crlBuilder = new X509CrlBuilder();

        crlBuilder.withIssuerDN(identityCertificate.getSubject());
        crlBuilder.withValidityPeriod(identityCertificate.getValidityPeriod());
        crlBuilder.withNumber(BigInteger.ONE);

        crlBuilder.withAuthorityKeyIdentifier(persistedKeyPair.getPublicKey());
        crlBuilder.withSignatureProvider(persistedKeyPair.getSignatureProvider());

        return crlBuilder.build(persistedKeyPair.getPrivateKey());
    }

    private ProvisioningIdentityCertificate createProvisioningIdentityCertificate(X500Principal identityCertificateSubject) {
        ProvisioningIdentityCertificateBuilder builder = new ProvisioningIdentityCertificateBuilder();
        builder.withSelfSigningKeyPair(getKeyPair());
        builder.withSelfSigningSubject(identityCertificateSubject);
        builder.withSignatureProvider(persistedKeyPair.getSignatureProvider());
        return builder.build();
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

    public ProvisioningCmsObject createProvisioningCmsResponseObject(SingleUseKeyPairFactory singleUseKeyPairFactory, AbstractProvisioningPayload responsePayload) {
        ProvisioningCmsObjectBuilder cmsObjectBuilder = new ProvisioningCmsObjectBuilder();

        cmsObjectBuilder.withPayloadContent(responsePayload);
        cmsObjectBuilder.withCrl(getProvisioningCrl());
        cmsObjectBuilder.withSignatureProvider(singleUseKeyPairFactory.signatureProvider());

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        cmsObjectBuilder.withCmsCertificate(createCmsCertificate(eeKeyPair).getCertificate());

        return cmsObjectBuilder.build(eeKeyPair.getPrivate());
    }

    private ProvisioningCmsCertificate createCmsCertificate(KeyPair eeKeyPair) {
        ProvisioningCmsCertificateBuilder builder = new ProvisioningCmsCertificateBuilder();

        builder.withIssuerDN(getProvisioningIdentityCertificate().getSubject());

        BigInteger serial = SerialNumberSupplier.getInstance().get();

        builder.withSerial(serial);
        builder.withPublicKey(eeKeyPair.getPublic());

        builder.withSubjectDN(new UuidRepositoryObjectNamingStrategy().getCertificateSubject(eeKeyPair.getPublic()));
        builder.withSigningKeyPair(getKeyPair());
        builder.withSignatureProvider(persistedKeyPair.getSignatureProvider());
        return builder.build();
    }

}
