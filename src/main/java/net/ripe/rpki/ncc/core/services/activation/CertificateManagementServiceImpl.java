package net.ripe.rpki.ncc.core.services.activation;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateBuilder;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.util.SerialNumberSupplier;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.EnumSet;
import java.util.List;

@Service
public class CertificateManagementServiceImpl implements CertificateManagementService {

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

    private final DistributionSummary manifestSizeDistribution;
    private final DistributionSummary crlSizeDistribution;

    @Autowired
    public CertificateManagementServiceImpl(ResourceCertificateRepository resourceCertificateRepository,
                                            PublishedObjectRepository publishedObjectRepository,
                                            CrlEntityRepository crlEntityRepository,
                                            ManifestEntityRepository manifestEntityRepository,
                                            SingleUseKeyPairFactory singleUseKeyPairFactory,
                                            MeterRegistry meterRegistry) {
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
        this.crlEntityRepository = crlEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.singleUseKeyPairFactory = singleUseKeyPairFactory;
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

    @Override
    public OutgoingResourceCertificate issueSingleUseEeResourceCertificate(ManagedCertificateAuthority hostedCa, CertificateIssuanceRequest request,
                                                                           ValidityPeriod validityPeriod, KeyPairEntity signingKeyPair) {
        IncomingResourceCertificate active = signingKeyPair.getCurrentIncomingCertificate();
        ResourceCertificateBuilder builder = new ResourceCertificateBuilder();
        if (request.getResources().isEmpty()) {
            builder.withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class));
        } else {
            Validate.isTrue(active.getResources().contains(request.getResources()), "EE certificate resources MUST BE contained in the parent certificate");
            builder.withResources(request.getResources());
        }
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
        addOutgoingResourceCertificate(result);
        return result;
    }

    @Override
    public void addOutgoingResourceCertificate(OutgoingResourceCertificate resourceCertificate) {
        resourceCertificateRepository.add(resourceCertificate);
    }

    @Override
    public boolean isManifestAndCrlUpdatedNeeded(ManagedCertificateAuthority certificateAuthority) {
        return certificateAuthority.getKeyPairs()
            .stream()
            .filter(KeyPairEntity::isPublishable)
            .anyMatch(keyPair -> {
                DateTime now = DateTime.now(DateTimeZone.UTC);

                CrlEntity crlEntity = crlEntityRepository.findOrCreateByKeyPair(keyPair);
                ManifestEntity manifestEntity = manifestEntityRepository.findOrCreateByKeyPairEntity(keyPair);

                return crlEntity.isUpdateNeeded(now, resourceCertificateRepository) || isManifestUpdateNeeded(now, manifestEntity);
            });
    }

    /**
     * Update MFTs and CRLs. The generated manifest's and CRL's next update time must be the same, so if either
     * object needs to be updated both are newly issued.
     *
     * Mark all new and updated objects to be published/withdrawn.
     * Emit corresponding event, so that an update is sent to the publication server.
     */
    @Override
    public long updateManifestAndCrlIfNeeded(ManagedCertificateAuthority certificateAuthority) {
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
                issueManifest(certificateAuthority, manifestEntity, validityPeriod);
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
                determineManifestEntries(publishedObjectRepository, keyPair),
                keyPair.getCurrentIncomingCertificate()
        );
    }

    private void issueManifest(ManagedCertificateAuthority certificateAuthority, ManifestEntity manifestEntity, ValidityPeriod validityPeriod) {
        KeyPairEntity keyPair = manifestEntity.getKeyPair();

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        CertificateIssuanceRequest request = manifestEntity.requestForManifestEeCertificate(eeKeyPair);
        OutgoingResourceCertificate manifestCertificate = issueSingleUseEeResourceCertificate(certificateAuthority, request, validityPeriod, keyPair);

        List<PublishedObject> manifestEntries = determineManifestEntries(publishedObjectRepository, keyPair);
        manifestEntity.update(manifestCertificate, eeKeyPair, singleUseKeyPairFactory.signatureProvider(), manifestEntries);
    }

    private static List<PublishedObject> determineManifestEntries(PublishedObjectRepository publishedObjectRepository, KeyPairEntity keyPair) {
        return publishedObjectRepository.findActiveManifestEntries(keyPair);
    }
}
