package net.ripe.rpki.domain.signing;

import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateBuilder;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.util.SerialNumberSupplier;

import java.net.URI;

public class ChildCertificateSigner {

    public OutgoingResourceCertificate buildOutgoingResourceCertificate(CertificateIssuanceRequest request, ValidityPeriod validityPeriod, KeyPairEntity signingKeyPair) {
        IncomingResourceCertificate signingCertificate = signingKeyPair.getCurrentIncomingCertificate();
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();

        ResourceCertificateBuilder builder = new ResourceCertificateBuilder()
                .withSerial(SerialNumberSupplier.getInstance().get())
                .withResourceExtension(request.getResourceExtension())
                .withSubjectDN(request.getSubjectDN())
                .withSubjectPublicKey(request.getSubjectPublicKey())
                .withSubjectInformationAccess(request.getSubjectInformationAccess())
                .withIssuerDN(signingCertificate.getSubject())
                .withValidityPeriod(validityPeriod)
                .withSigningKeyPair(signingKeyPair)
                .withCa(true)
                .withEmbedded(false)
                .withAuthorityInformationAccess(ias.aiaForCertificate(signingCertificate))
                .withParentPublicationDirectory(extractPublicationDirectory(signingCertificate))
                .withFilename(ias.caCertificateFilename(request.getSubjectPublicKey()))
                .withCrlDistributionPoints(signingKeyPair.crlLocationUri());

        return builder.build();
    }

    private URI extractPublicationDirectory(ResourceCertificate resourceCertificate) {
        return CertificateInformationAccessUtil.extractPublicationDirectory(resourceCertificate.getSia());
    }
}
