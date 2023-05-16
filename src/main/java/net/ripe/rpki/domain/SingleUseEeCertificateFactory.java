package net.ripe.rpki.domain;

import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SingleUseEeCertificateFactory {

    private final ResourceCertificateRepository resourceCertificateRepository;

    @Autowired
    public SingleUseEeCertificateFactory(ResourceCertificateRepository resourceCertificateRepository) {
        this.resourceCertificateRepository = resourceCertificateRepository;
    }

    public OutgoingResourceCertificate issueSingleUseEeResourceCertificate(
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
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
        builder.withAuthorityInformationAccess(ias.aiaForCertificate(active));
        builder.withCrlDistributionPoints(signingKeyPair.crlLocationUri());
        builder.withSubjectInformationAccess(request.getSubjectInformationAccess());
        OutgoingResourceCertificate result = builder.build();
        resourceCertificateRepository.add(result);
        return result;
    }
}
