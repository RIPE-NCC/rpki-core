package net.ripe.rpki.domain.manifest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.aspa.AspaEntityService;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.roa.RoaEntityService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.List;

@Service
public class ManifestPublicationService {
    /**
     * Time to next update for CRL and manifest. Both objects must have the same next update time to be valid.
     */
    public static final Period TIME_TO_NEXT_UPDATE = Period.hours(24);

    public static final String RPKI_CA_GENERATED_MANIFEST_SIZE_METRIC_NAME = "rpki.ca.generated.manifest.size";
    public static final String RPKI_CA_GENERATED_CRL_SIZE_METRIC_NAME = "rpki.ca.generated.crl.size";

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final CrlEntityRepository crlEntityRepository;
    private final ManifestEntityRepository manifestEntityRepository;
    private final SingleUseKeyPairFactory singleUseKeyPairFactory;
    private final SingleUseEeCertificateFactory singleUseEeCertificateFactory;

    private final DistributionSummary manifestSizeDistribution;
    private final DistributionSummary crlSizeDistribution;
    private final AspaEntityService aspaEntityService;
    private final RoaEntityService roaEntityService;

    @Autowired
    public ManifestPublicationService(
        ResourceCertificateRepository resourceCertificateRepository,
        PublishedObjectRepository publishedObjectRepository,
        AspaEntityService aspaEntityService,
        RoaEntityService roaEntityService,
        CrlEntityRepository crlEntityRepository,
        ManifestEntityRepository manifestEntityRepository,
        SingleUseKeyPairFactory singleUseKeyPairFactory,
        SingleUseEeCertificateFactory singleUseEeCertificateFactory,
        MeterRegistry meterRegistry
    ) {
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
        this.aspaEntityService = aspaEntityService;
        this.roaEntityService = roaEntityService;
        this.crlEntityRepository = crlEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.singleUseKeyPairFactory = singleUseKeyPairFactory;
        this.singleUseEeCertificateFactory = singleUseEeCertificateFactory;
        this.manifestSizeDistribution = DistributionSummary.builder(RPKI_CA_GENERATED_MANIFEST_SIZE_METRIC_NAME)
            .description("size in bytes of generated manifests")
            .baseUnit("byte")
            .minimumExpectedValue(100d)
            .maximumExpectedValue(2_000_000d)
            .publishPercentileHistogram()
            .register(meterRegistry);
        this.crlSizeDistribution = DistributionSummary.builder(RPKI_CA_GENERATED_CRL_SIZE_METRIC_NAME)
            .description("size in bytes of generated CRLs")
            .baseUnit("byte")
            .minimumExpectedValue(100d)
            .maximumExpectedValue(2_000_000d)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    /**
     * Update MFTs and CRLs. The generated manifest's and CRL's next update time must be the same, so if either
     * object needs to be updated both are newly issued.
     * <p>
     * Mark all new and updated objects to be published/withdrawn.
     * Emit corresponding event, so that an update is sent to the publication server.
     */
    public long updateManifestAndCrlIfNeeded(ManagedCertificateAuthority certificateAuthority) {
        aspaEntityService.updateAspaIfNeeded(certificateAuthority);
        roaEntityService.updateRoasIfNeeded(certificateAuthority);

        return certificateAuthority.getKeyPairs()
            .stream()
            .filter(KeyPairEntity::isPublishable)
            .filter(keyPair -> {
                DateTime now = DateTime.now(DateTimeZone.UTC);

                CrlEntity crlEntity = crlEntityRepository.findOrCreateByKeyPair(keyPair);
                ManifestEntity manifestEntity = manifestEntityRepository.findOrCreateByKeyPairEntity(keyPair);

                boolean updateNeeded = crlEntity.isUpdateNeeded(now, resourceCertificateRepository) || isManifestUpdateNeeded(now, manifestEntity);
                if (!updateNeeded) {
                    return false;
                }

                ValidityPeriod validityPeriod = new ValidityPeriod(now, now.plus(TIME_TO_NEXT_UPDATE));

                if (manifestEntity.getCertificate() != null) {
                    // The manifest certificate must be revoked before we issue a new CRL, otherwise
                    // the certificate's serial will not be included in the new CRL.
                    manifestEntity.getCertificate().revoke();
                }

                // Issue the CRL before issuing the manifest, so that the new CRL will appear on the manifest.
                crlEntity.update(validityPeriod, resourceCertificateRepository);
                crlEntityRepository.add(crlEntity);

                // Issue the manifest with a one-time-use EE certificate. The validity times of the EE
                // certificate MUST exactly match the 'thisUpdate' and 'nextUpdate' times in the manifest.
                //
                // This is implemented by first creating the EE certificate with the timings in
                // 'validityPeriod'. Then the manifest is updated with the certificate, copying the timings
                // into the manifest (see ManifestEntity#update).
                issueManifest(manifestEntity, validityPeriod);
                manifestEntityRepository.add(manifestEntity);

                crlSizeDistribution.record(crlEntity.getEncoded().length);
                manifestSizeDistribution.record(manifestEntity.getEncoded().length);

                return true;
            }).count();
    }

    private boolean isManifestUpdateNeeded(DateTime now, ManifestEntity manifestEntity) {
        KeyPairEntity keyPair = manifestEntity.getKeyPair();
        return manifestEntity.isUpdateNeeded(
            now,
            determineManifestEntries(publishedObjectRepository, keyPair)
        );
    }

    private void issueManifest(ManifestEntity manifestEntity, ValidityPeriod validityPeriod) {
        KeyPairEntity keyPair = manifestEntity.getKeyPair();

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        CertificateIssuanceRequest request = manifestEntity.requestForManifestEeCertificate(eeKeyPair);
        OutgoingResourceCertificate manifestCertificate = singleUseEeCertificateFactory.issueSingleUseEeResourceCertificate(request, validityPeriod, keyPair);

        List<PublishedObject> manifestEntries = determineManifestEntries(publishedObjectRepository, keyPair);
        manifestEntity.update(manifestCertificate, eeKeyPair, singleUseKeyPairFactory.signatureProvider(), manifestEntries);
    }

    private static List<PublishedObject> determineManifestEntries(PublishedObjectRepository publishedObjectRepository, KeyPairEntity keyPair) {
        return publishedObjectRepository.findActiveManifestEntries(keyPair);
    }
}
