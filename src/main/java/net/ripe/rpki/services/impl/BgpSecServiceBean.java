package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.bgpsec.*;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import net.ripe.rpki.server.api.services.read.BgpSecViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
@Transactional(readOnly = true)
@Slf4j
public class BgpSecServiceBean implements BgpSecViewService {

    private final CertificateAuthorityRepository caRepository;
    private final BgpSecConfigurationRepository bgpSecConfigurationRepository;
    private final BgpSecEntityRepository bgpSecEntityRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;

    public BgpSecServiceBean(CertificateAuthorityRepository caRepository,
                             BgpSecConfigurationRepository bgpSecConfigurationRepository,
                             BgpSecEntityRepository bgpSecEntityRepository,
                             TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository) {
        this.caRepository = caRepository;
        this.bgpSecConfigurationRepository = bgpSecConfigurationRepository;
        this.bgpSecEntityRepository = bgpSecEntityRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
    }

    @Override
    public List<BgpSecConfigurationData> findBgpSecConfiguration(long caId) {
        ManagedCertificateAuthority ca = caRepository.findManagedCa(caId);
        if (ca == null) {
            return Collections.emptyList();
        }
        return bgpSecConfigurationRepository.findByCertificateAuthority(ca)
                .stream().map(BgpSecConfiguration::withId).toList();
    }

    @Override
    public Optional<BgpSecConfigurationData> findBgpSecConfigurationById(long caId, long id) {
        ManagedCertificateAuthority ca = caRepository.findManagedCa(caId);
        if (ca == null) {
            return Optional.empty();
        }
        return bgpSecConfigurationRepository.findByCertificateAuthority(ca)
                .stream()
                    .filter(x -> x.getId().equals(id))
                    .map(BgpSecConfiguration::toData)
                    .findFirst();
    }

    @Override
    public Optional<byte[]> findBgpSecCertificatePkcs7(
            long caId,
            BgpSecConfigurationData data
    ) {
        ManagedCertificateAuthority ca = caRepository.findManagedCa(caId);
        if (ca == null) {
            return Optional.empty();
        }

        return findBgpSecEntity(ca, data)
                .map(entity -> packAsPkcs7(
                        List.of(entity.getCertificate()
                                .getCertificate()
                                .getCertificate())
                ));
    }


    @Override
    public Optional<byte[]> findBgpSecCertificateChainPkcs7(long caId, BgpSecConfigurationData id) {
        ManagedCertificateAuthority ca = caRepository.findManagedCa(caId);
        if (ca == null) {
            return Optional.empty();
        }
        return findBgpSecEntity(ca, id)
                .map(entity -> packChainAsPkcs7(entity, ca));
    }

    private Optional<BgpSecEntity> findBgpSecEntity(
            ManagedCertificateAuthority ca,
            BgpSecConfigurationData data
    ) {
        return bgpSecEntityRepository.findCurrentByCertificateAuthority(ca)
                .stream()
                .filter(entity -> entity.getAsn().equals(data.asn()))
                .filter(entity -> Objects.equals(entity.getRouterId(), data.routerId().value()))
                .filter(entity -> entity.getKeyIdentifier().equalsIgnoreCase(data.keyIdentifier())).findFirst();
    }


    private byte[] packChainAsPkcs7(BgpSecEntity entity, ManagedCertificateAuthority ca) {
        BgpSecCertificate bgpSecCertificate = entity.getCertificate();
        KeyPairEntity signingKeyPair = bgpSecCertificate.getSigningKeyPair();
        IncomingResourceCertificate issuerCertificate = signingKeyPair.findCurrentIncomingCertificate()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing incoming certificate for BGPSec signing key " + entity.getKeyIdentifier()));

        List<X509Certificate> chain = new ArrayList<>();
        chain.add(bgpSecCertificate.getCertificate().getCertificate());
        chain.add(issuerCertificate.getCertificate().getCertificate());

        ParentCertificateAuthority parent = ca.getParent();
        while (parent instanceof ManagedCertificateAuthority managedParent) {
            IncomingResourceCertificate parentIncomingCertificate = managedParent.findCurrentIncomingResourceCertificate()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing incoming certificate for parent CA " + managedParent.getName()));
            chain.add(parentIncomingCertificate.getCertificate().getCertificate());
            parent = managedParent.getParent();
        }

        if (parent == null) {
            // We reached TA
            trustAnchorPublishedObjectRepository.findActiveObjects()
                    .stream()
                    .filter(o -> o.getStatus() == PublicationStatus.PUBLISHED)
                    .filter(o -> o.getUri().getPath().endsWith(".cer"))
                    .map(o -> {
                        try {
                            var parser = new X509ResourceCertificateParser();
                            parser.parse(ValidationResult.withLocation("ta-cert"), o.getContent());
                            return parser.getCertificate();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(BgpSecServiceBean::isTaCertificate)
                    .max(Comparator.comparing(c -> c.getValidityPeriod().getNotValidAfter()))
                    .ifPresent(taCert -> chain.add(taCert.getCertificate()));
        }

        var subjects = chain.stream().map(c -> c.getSubjectX500Principal().getName()).toList();
        var kis = chain.stream().map(c -> HexFormat.of().formatHex(X509CertificateUtil.getSubjectKeyIdentifier(c))).toList();
        log.info("BGPSec certificate chain: {}", subjects);
        log.info("BGPSec certificate chain key identifiers: {}", kis);

        return packAsPkcs7(chain);
    }

    // TA certificate is the one that has no AKI or AKI == SKI
    private static boolean isTaCertificate(X509ResourceCertificate c) {
        return c.getAuthorityKeyIdentifier() == null ||
                Arrays.equals(c.getSubjectKeyIdentifier(), c.getAuthorityKeyIdentifier());
    }

    private byte[] packAsPkcs7(List<X509Certificate> certs) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            CertPath certPath = certificateFactory.generateCertPath(certs);
            return certPath.getEncoded("PKCS7");
        } catch (CertificateException e) {
            throw new IllegalStateException("Failed to build PKCS#7 certificate bundle", e);
        }
    }
}
