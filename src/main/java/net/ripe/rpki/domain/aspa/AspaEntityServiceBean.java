package net.ripe.rpki.domain.aspa;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedMapDifference;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCms;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCmsBuilder;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.IncomingCertificateRevokedEvent;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.SingleUseEeCertificateFactory;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.naming.RepositoryObjectNamingStrategy;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.services.command.UnparseableRpkiObjectException;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.toSortedMap;

@Service
@Slf4j
public class AspaEntityServiceBean implements AspaEntityService, CertificateAuthorityEventVisitor {
    public static final long CURRENT_ASPA_PROFILE_VERSION = 16L;

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final AspaConfigurationRepository aspaConfigurationRepository;
    private final AspaEntityRepository aspaEntityRepository;
    private final SingleUseKeyPairFactory singleUseKeyPairFactory;
    private final SingleUseEeCertificateFactory singleUseEeCertificateFactory;
    private final ResourceCertificateInformationAccessStrategy informationAccessStrategy = new ResourceCertificateInformationAccessStrategyBean();

    @Inject
    public AspaEntityServiceBean(
        CertificateAuthorityRepository certificateAuthorityRepository,
        AspaConfigurationRepository aspaConfigurationRepository,
        AspaEntityRepository aspaEntityRepository,
        SingleUseKeyPairFactory singleUseKeyPairFactory,
        SingleUseEeCertificateFactory singleUseEeCertificateFactory
    ) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.aspaConfigurationRepository = aspaConfigurationRepository;
        this.aspaEntityRepository = aspaEntityRepository;
        this.singleUseKeyPairFactory = singleUseKeyPairFactory;
        this.singleUseEeCertificateFactory = singleUseEeCertificateFactory;
    }

    @Override
    public void visitKeyPairActivatedEvent(KeyPairActivatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityVersionedId().getId());
        updateAspaIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityId());
        updateAspaIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateRevokedEvent(IncomingCertificateRevokedEvent event, CommandContext context) {
        // All ASPA entities are already revoked and removed by the key pair deletion service in this case.
    }

    /**
     * Validates the current ASPA entities against the ASPA configuration and incoming resources.
     *
     * @return a pair with ASPA entities that are no longer valid and ASPA configurations per ASN that are
     * not matched by the current resource certificate and configuration. When both lists are empty the entities
     * are fully up-to-date with the configuration.
     */
    public Pair<Collection<AspaEntity>, SortedMap<Asn, AspaConfiguration>> validateAspaConfiguration(ManagedCertificateAuthority ca) {
        // Not all ASPA entities can necessarily be parsed (e.g. profile change). While all ASNs should have 0..1 ASPA,
        // there can be many unparesable ASPAs
        List<AspaEntity> aspaEntities = aspaEntityRepository.findCurrentByCertificateAuthority(ca);

        Optional<IncomingResourceCertificate> maybeCurrentIncomingResourceCertificate = ca.findCurrentIncomingResourceCertificate();
        if (!maybeCurrentIncomingResourceCertificate.isPresent()) {
            // No current resource certificate, so all ASPA entities are invalid and without resources there is
            // no applicable configuration
            return Pair.of(aspaEntities, Collections.emptySortedMap());
        }

        IncomingResourceCertificate incomingResourceCertificate = maybeCurrentIncomingResourceCertificate.get();

        Map<Boolean, List<AspaEntity>> validatedAspaEntities = aspaEntities.stream()
                .collect(Collectors.partitioningBy(aspa -> isValidAspaEntity(incomingResourceCertificate, aspa)));

        // Aspa Configurations covered by resource certificate
        SortedMap<Asn, AspaConfiguration> aspaConfiguration = aspaConfigurationRepository.findConfigurationsWithProvidersByCertificateAuthority(ca)
            .values()
            .stream()
            .filter(x -> incomingResourceCertificate.getCertifiedResources().contains(x.getCustomerAsn()))
            .collect(toSortedMap(AspaConfiguration::getCustomerAsn, x -> x));

        SortedMapDifference<Asn, SortedSet<Asn>> difference = Maps.difference(
            AspaConfiguration.entitiesToMaps(aspaConfiguration),
            AspaEntity.entitiesToMaps(validatedAspaEntities.get(true))
        );

        Map<Asn, AspaEntity> validAspaEntitiesByAsn = validatedAspaEntities.get(true)
            .stream()
            .collect(Collectors.toMap(AspaEntity::getCustomerAsn, x -> x));

        List<AspaEntity> invalidAspaEntities = Stream.concat(
                validatedAspaEntities.get(false).stream(),
                Stream.concat(
                        difference.entriesOnlyOnRight().keySet().stream(),
                        difference.entriesDiffering().keySet().stream()
                ).map(validAspaEntitiesByAsn::get)
            )
            .collect(Collectors.toList());

        SortedMap<Asn, AspaConfiguration> unmatchedAspaConfiguration = Stream.concat(
                difference.entriesOnlyOnLeft().keySet().stream(),
                difference.entriesDiffering().keySet().stream()
            )
            .collect(toSortedMap(x -> x, aspaConfiguration::get));

        return Pair.of(invalidAspaEntities, unmatchedAspaConfiguration);
    }

    @Override
    public void updateAspaIfNeeded(ManagedCertificateAuthority ca) {
        Pair<Collection<AspaEntity>, SortedMap<Asn, AspaConfiguration>> validated = validateAspaConfiguration(ca);
        if (!validated.getLeft().isEmpty() || !validated.getRight().isEmpty()) {
            log.debug("revoking {} and issuing {} ASPA entities", validated.getLeft().size(), validated.getRight().size());
        }

        for (AspaEntity aspaEntity : validated.getLeft()) {
            aspaEntity.revokeAndRemove(aspaEntityRepository);
        }
        for (AspaConfiguration aspaConfiguration : validated.getRight().values()) {
            Optional<AspaEntity> aspaEntity = createAspaEntity(ca, aspaConfiguration);
            aspaEntity.ifPresent(aspaEntityRepository::add);
        }
    }

    private static boolean isValidAspaEntity(IncomingResourceCertificate incomingResourceCertificate, AspaEntity aspa) {
        boolean isValidAndCurrent = false;
        try {
            isValidAndCurrent = aspa.getCertificate().isValid()
                    && aspa.getCertificate().getSigningKeyPair().isCurrent()
                    && aspa.getProfileVersion() == CURRENT_ASPA_PROFILE_VERSION
                    && Objects.equals(incomingResourceCertificate.getPublicationUri(), aspa.getAspaCms().getParentCertificateUri())
                    && incomingResourceCertificate.getCertifiedResources().contains(aspa.getCustomerAsn());

            return isValidAndCurrent;
        } catch (UnparseableRpkiObjectException e) {
            return false;
        } finally {
            try {
                if (!isValidAndCurrent && log.isInfoEnabled()) {
                    log.info("Will re-issue ASPA at {} certificate-valid={} keypair-current={} profile-version={} (current={}) parent-uri={} resources-match={}",
                            aspa.getCertificate().isValid(), aspa.getCertificate().getSigningKeyPair().isCurrent(), aspa.getProfileVersion(), CURRENT_ASPA_PROFILE_VERSION,
                            Objects.equals(incomingResourceCertificate.getPublicationUri(), aspa.getAspaCms().getParentCertificateUri()), incomingResourceCertificate.getCertifiedResources().contains(aspa.getCustomerAsn())
                    );
                }
            } catch (Exception e) {
                // Ignore exceptions while printing debug message.
            }
        }
    }

    /**
     * Create AspaEntity if possible
     * @param certificateAuthority CA to issue under
     * @param aspaConfiguration configuration to apply
     * @return AspaEntity if possible, or empty.
     */
    @VisibleForTesting
    Optional<AspaEntity> createAspaEntity(ManagedCertificateAuthority certificateAuthority, AspaConfiguration aspaConfiguration) {
        // Filter out configurations that can not result in a valid ASPA entity, and would cause failures when trying
        // to get the CMS payload
        if (aspaConfiguration.getProviders().isEmpty() || !certificateAuthority.getCertifiedResources().contains(aspaConfiguration.getCustomerAsn())) {
            return Optional.empty();
        }

        DateTime now = DateTime.now(DateTimeZone.UTC);

        KeyPairEntity currentKeyPair = certificateAuthority.getCurrentKeyPair();
        IncomingResourceCertificate incomingResourceCertificate = currentKeyPair.getCurrentIncomingCertificate();
        ValidityPeriod validityPeriod = new ValidityPeriod(now, incomingResourceCertificate.getNotValidAfter());

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        OutgoingResourceCertificate endEntityCertificate = createEndEntityCertificate(aspaConfiguration.getCustomerAsn(), validityPeriod, eeKeyPair, currentKeyPair);

        AspaCms aspaCms = generateAspaCms(aspaConfiguration, eeKeyPair, endEntityCertificate.getCertificate());
        URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(
            currentKeyPair.getCurrentIncomingCertificate().getSia());
        return Optional.of(new AspaEntity(endEntityCertificate, aspaCms,
            informationAccessStrategy.aspaFilename(endEntityCertificate), publicationDirectory, CURRENT_ASPA_PROFILE_VERSION));
    }

    private OutgoingResourceCertificate createEndEntityCertificate(
        Asn customerAsn, ValidityPeriod validityPeriod, KeyPair eeKeyPair, KeyPairEntity signingKeyPair
    ) {
        CertificateIssuanceRequest request = requestEeCertificate(customerAsn, signingKeyPair, eeKeyPair);
        return singleUseEeCertificateFactory.issueSingleUseEeResourceCertificate(
            request, validityPeriod, signingKeyPair);
    }

    private AspaCms generateAspaCms(AspaConfiguration aspaConfiguration, KeyPair eeKeyPair, X509ResourceCertificate endEntityX509ResourceCertificate) {
        AspaCmsBuilder builder = new AspaCmsBuilder();
        builder.withCertificate(endEntityX509ResourceCertificate);
        builder.withCustomerAsn(aspaConfiguration.getCustomerAsn());
        builder.withProviderASSet(aspaConfiguration.getProviders());

        builder.withSignatureProvider(singleUseKeyPairFactory.signatureProvider());
        return builder.build(eeKeyPair.getPrivate());
    }

    private CertificateIssuanceRequest requestEeCertificate(Asn customerAsn, KeyPairEntity signingKeyPair, KeyPair eeKeyPair) {
        X500Principal subject = informationAccessStrategy.eeCertificateSubject(eeKeyPair.getPublic());
        X509CertificateInformationAccessDescriptor[] sia = informationAccessStrategy.siaForSignedObjectCertificate(signingKeyPair,
            RepositoryObjectNamingStrategy.ASPA_FILE_EXTENSION, subject, eeKeyPair.getPublic());
        return new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.of(customerAsn)), subject, eeKeyPair.getPublic(), sia);
    }
}
