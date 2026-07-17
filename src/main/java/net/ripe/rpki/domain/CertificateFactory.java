package net.ripe.rpki.domain;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificateBuilder;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificate;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang.Validate;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CertificateFactory {

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final BgpSecCertificateRepository bgpSecCertificateRepository;
    private final CertificationProviderConfigurationData providerConfigurationData;

    @Autowired
    public CertificateFactory(ResourceCertificateRepository resourceCertificateRepository,
                              BgpSecCertificateRepository bgpSecCertificateRepository,
                              CertificationProviderConfigurationData providerConfigurationData) {
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.bgpSecCertificateRepository = bgpSecCertificateRepository;
        this.providerConfigurationData = providerConfigurationData;
    }

    public OutgoingResourceCertificate issueAndPersistSingleUseEeResourceCertificate(
            CertificateIssuanceRequest request,
            ValidityPeriod validityPeriod,
            KeyPairEntity signingKeyPair
    ) {
        IncomingResourceCertificate active = signingKeyPair.getCurrentIncomingCertificate();
        Validate.isTrue(active.getResources().contains(request.getResourceExtension().getResources()), "EE certificate resources MUST BE contained in the parent certificate");
        ResourceCertificateBuilder builder = new ResourceCertificateBuilder();
        builder.withResourceExtension(request.getResourceExtension());
        builder.withSerial(SerialNumberSupplier.getInstance().get());
        builder.withSubjectDN(request.getSubjectDN());
        builder.withSubjectPublicKey(request.getSubjectPublicKey());
        builder.withSubjectInformationAccess(request.getSubjectInformationAccess());
        builder.withIssuerDN(active.getSubject());
        builder.withValidityPeriod(validityPeriod);
        builder.withSigningKeyPair(signingKeyPair);
        builder.withCa(false).withEmbedded(true);
        builder.withAuthorityInformationAccess(new ResourceCertificateInformationAccessStrategyBean().aiaForCertificate(active));
        builder.withCrlDistributionPoints(signingKeyPair.crlLocationUri());
        builder.withSubjectInformationAccess(request.getSubjectInformationAccess());
        OutgoingResourceCertificate certificate = builder.build();
        resourceCertificateRepository.add(certificate);
        return certificate;
    }

    public BgpSecCertificate issueAndPersistBgpSecCertificate(
            CertificateIssuanceRequest request,
            Asn asn,
            ValidityPeriod validityPeriod,
            KeyPairEntity signingKeyPair) {

        IncomingResourceCertificate currentCert = signingKeyPair.getCurrentIncomingCertificate();
        X509RouterCertificateBuilder builder = new X509RouterCertificateBuilder();
        builder.withKeyUsage(KeyUsage.digitalSignature);
        builder.withSignatureProvider(providerConfigurationData.getSignatureProvider());
        builder.withAsns(new int[]{asn.getValue().intValue()});
        builder.withSerial(SerialNumberSupplier.getInstance().get());
        builder.withSubjectDN(request.getSubjectDN());
        builder.withPublicKey(request.getSubjectPublicKey());
        builder.withIssuerDN(currentCert.getSubject());
        builder.withValidityPeriod(validityPeriod);
        builder.withSigningKeyPair(signingKeyPair.getKeyPair());
        ResourceCertificateInformationAccessStrategy informationAccessStrategy = new ResourceCertificateInformationAccessStrategyBean();
        builder.withAuthorityInformationAccess(informationAccessStrategy.aiaForCertificate(currentCert));
        builder.withCrlDistributionPoints(signingKeyPair.crlLocationUri());
        X509RouterCertificate certificate = builder.build();
        var bgpSecCertificate = new BgpSecCertificate(certificate, signingKeyPair, asn);
        bgpSecCertificateRepository.add(bgpSecCertificate);
        return bgpSecCertificate;
    }
}
