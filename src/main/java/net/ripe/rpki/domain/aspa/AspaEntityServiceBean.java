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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

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
        if (ca == null) {
            return;
        }

        revokeAspasSignedByOldKeys(ca);
    }

    @Override
    public void updateAspaIfNeeded(ManagedCertificateAuthority ca) {
        Optional<IncomingResourceCertificate> maybeCurrentIncomingResourceCertificate = ca.findCurrentIncomingResourceCertificate();
        if (!maybeCurrentIncomingResourceCertificate.isPresent()) {
            return;
        }

        IncomingResourceCertificate incomingResourceCertificate = maybeCurrentIncomingResourceCertificate.get();
        ImmutableResourceSet certifiedResources = incomingResourceCertificate.getResources();

        SortedMap<Asn, AspaEntity> aspaEntities = loadValidAspaEntities(ca, incomingResourceCertificate);
        SortedMap<Asn, AspaConfiguration> aspaConfiguration = aspaConfigurationRepository.findByCertificateAuthority(ca);

        SortedMapDifference<Asn, SortedMap<Asn, AspaAfiLimit>> difference = Maps.difference(
            AspaConfiguration.entitiesToMaps(aspaConfiguration),
            AspaEntity.entitiesToMaps(aspaEntities)
        );

        Stream<Asn> obsoleteAspaEntityCustomerAsns = Stream.concat(difference.entriesOnlyOnRight().keySet().stream(), difference.entriesDiffering().keySet().stream());
        obsoleteAspaEntityCustomerAsns.forEach(obsoleteAsn -> {
            AspaEntity aspaEntity = aspaEntities.get(obsoleteAsn);
            aspaEntity.revokeAndRemove(aspaEntityRepository);
        });

        Stream<Asn> currentAspaConfigurationCustomerAsns = Stream.concat(difference.entriesOnlyOnLeft().keySet().stream(), difference.entriesDiffering().keySet().stream());
        currentAspaConfigurationCustomerAsns.forEach(customerAsn -> {
            if (!certifiedResources.contains(customerAsn)) {
                log.warn("configured customer ASN '{}' is not part of currently certified resources for CA '{}'", customerAsn, ca.getName());
            } else {
                AspaEntity aspaEntity = createAspaEntity(ca, aspaConfiguration.get(customerAsn));
                aspaEntityRepository.add(aspaEntity);
            }
        });
    }

    private void revokeAspasSignedByOldKeys(ManagedCertificateAuthority ca) {
        ca.getKeyPairs().stream()
            .filter(KeyPairEntity::isOld)
            .flatMap(kp -> aspaEntityRepository.findByCertificateSigningKeyPair(kp).stream())
            .filter(aspaEntity ->  !aspaEntity.isRevoked())
            .forEach(aspaEntity -> aspaEntity.revokeAndRemove(aspaEntityRepository));
    }

    private SortedMap<Asn, AspaEntity> loadValidAspaEntities(ManagedCertificateAuthority ca, IncomingResourceCertificate incomingResourceCertificate) {
        Map<Boolean, List<AspaEntity>> validatedAspas = aspaEntityRepository.findByCertificateSigningKeyPair(ca.getCurrentKeyPair())
            .stream()
            .collect(Collectors.partitioningBy(aspa -> isValidAspa(incomingResourceCertificate, aspa)));

        validatedAspas.get(false).forEach(aspa -> aspa.revokeAndRemove(aspaEntityRepository));

        return streamToSortedMap(
            validatedAspas.get(true).stream(),
            AspaEntity::getCustomerAsn,
            Function.identity()
        );
    }

    private static boolean isValidAspa(IncomingResourceCertificate incomingResourceCertificate, AspaEntity aspa) {
        return aspa.getCertificate().isValid()
            && Objects.equals(incomingResourceCertificate.getPublicationUri(), aspa.getAspaCms().getParentCertificateUri())
            && incomingResourceCertificate.getResources().contains(aspa.getCustomerAsn());
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
