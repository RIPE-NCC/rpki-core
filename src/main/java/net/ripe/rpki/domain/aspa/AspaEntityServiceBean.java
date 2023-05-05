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
import net.ripe.rpki.commons.crypto.cms.aspa.ProviderAS;
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
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
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
        SortedMap<Asn, AspaEntity> aspaEntities = aspaEntityRepository.findCurrentByCertificateAuthority(ca).stream()
            .collect(toSortedMap(AspaEntity::getCustomerAsn, x -> x));

        Optional<IncomingResourceCertificate> maybeCurrentIncomingResourceCertificate = ca.findCurrentIncomingResourceCertificate();
        if (!maybeCurrentIncomingResourceCertificate.isPresent()) {
            // No current resource certificate, so all ASPA entities are invalid and without resources there is
            // no applicable configuration
            return Pair.of(aspaEntities.values(), Collections.emptySortedMap());
        }

        IncomingResourceCertificate incomingResourceCertificate = maybeCurrentIncomingResourceCertificate.get();

        Map<Boolean, List<AspaEntity>> validatedAspaEntities = aspaEntities.values().stream()
            .collect(Collectors.partitioningBy(aspa -> isValidAspaEntity(incomingResourceCertificate, aspa)));

        SortedMap<Asn, AspaConfiguration> aspaConfiguration = aspaConfigurationRepository.findByCertificateAuthority(ca)
            .values()
            .stream()
            .filter(x -> incomingResourceCertificate.getCertifiedResources().contains(x.getCustomerAsn()))
            .collect(toSortedMap(AspaConfiguration::getCustomerAsn, x -> x));

        SortedMapDifference<Asn, SortedMap<Asn, AspaAfiLimit>> difference = Maps.difference(
            AspaConfiguration.entitiesToMaps(aspaConfiguration),
            AspaEntity.entitiesToMaps(validatedAspaEntities.get(true))
        );

        List<AspaEntity> invalidAspaEntities = Stream.concat(
                validatedAspaEntities.get(false).stream(),
                Stream.concat(
                        difference.entriesOnlyOnRight().keySet().stream(),
                        difference.entriesDiffering().keySet().stream()
                    )
                    .map(aspaEntities::get)
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
            AspaEntity aspaEntity = createAspaEntity(ca, aspaConfiguration);
            aspaEntityRepository.add(aspaEntity);
        }
    }

    private static boolean isValidAspaEntity(IncomingResourceCertificate incomingResourceCertificate, AspaEntity aspa) {
        try {
            return aspa.getCertificate().isValid()
                && aspa.getCertificate().getSigningKeyPair().isCurrent()
                && Objects.equals(incomingResourceCertificate.getPublicationUri(), aspa.getAspaCms().getParentCertificateUri())
                && incomingResourceCertificate.getCertifiedResources().contains(aspa.getCustomerAsn());
        } catch (UnparseableRpkiObjectException e) {
            return false;
        }
    }

    @VisibleForTesting
    AspaEntity createAspaEntity(ManagedCertificateAuthority certificateAuthority, AspaConfiguration aspaConfiguration) {
        DateTime now = DateTime.now(DateTimeZone.UTC);

        KeyPairEntity currentKeyPair = certificateAuthority.getCurrentKeyPair();
        IncomingResourceCertificate incomingResourceCertificate = currentKeyPair.getCurrentIncomingCertificate();
        ValidityPeriod validityPeriod = new ValidityPeriod(now, incomingResourceCertificate.getNotValidAfter());

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        OutgoingResourceCertificate endEntityCertificate = createEndEntityCertificate(aspaConfiguration.getCustomerAsn(), validityPeriod, eeKeyPair, currentKeyPair);

        AspaCms aspaCms = generateAspaCms(aspaConfiguration, eeKeyPair, endEntityCertificate.getCertificate());
        URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(
            currentKeyPair.getCurrentIncomingCertificate().getSia());
        return new AspaEntity(endEntityCertificate, aspaCms,
            informationAccessStrategy.aspaFilename(endEntityCertificate), publicationDirectory);
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
        builder.withProviderASSet(aspaConfiguration.getProviders().entrySet().stream()
            .map(entry -> new ProviderAS(entry.getKey(), entry.getValue().toOptionalAddressFamily()))
            .collect(Collectors.toList())
        );
        builder.withSignatureProvider(singleUseKeyPairFactory.signatureProvider());
        return builder.build(eeKeyPair.getPrivate());
    }

    private CertificateIssuanceRequest requestEeCertificate(Asn customerAsn, KeyPairEntity signingKeyPair, KeyPair eeKeyPair) {
        X500Principal subject = informationAccessStrategy.eeCertificateSubject(eeKeyPair.getPublic());
        X509CertificateInformationAccessDescriptor[] sia = informationAccessStrategy.siaForSignedObjectCertificate(signingKeyPair,
            RepositoryObjectNamingStrategy.ASPA_FILE_EXTENSION, subject, eeKeyPair.getPublic());
        return new CertificateIssuanceRequest(ImmutableResourceSet.of(customerAsn), subject, eeKeyPair.getPublic(), sia);
    }
}
