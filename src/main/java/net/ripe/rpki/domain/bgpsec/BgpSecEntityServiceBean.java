package net.ripe.rpki.domain.bgpsec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.IncomingCertificateRevokedEvent;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.services.command.UnparseableRpkiObjectException;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BgpSecEntityServiceBean implements BgpSecEntityService, CertificateAuthorityEventVisitor {

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final BgpSecConfigurationRepository bgpSecConfigurationRepository;
    private final BgpSecEntityRepository bgpSecEntityRepository;
    private final CertificateFactory certificateFactory;
    private final ResourceCertificateInformationAccessStrategy informationAccessStrategy = new ResourceCertificateInformationAccessStrategyBean();

    @Inject
    public BgpSecEntityServiceBean(
            CertificateAuthorityRepository certificateAuthorityRepository,
            BgpSecConfigurationRepository bgpSecConfigurationRepository,
            BgpSecEntityRepository bgpSecEntityRepository,
            CertificateFactory certificateFactory
    ) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.bgpSecConfigurationRepository = bgpSecConfigurationRepository;
        this.bgpSecEntityRepository = bgpSecEntityRepository;
        this.certificateFactory = certificateFactory;
    }

    @Override
    public void visitKeyPairActivatedEvent(KeyPairActivatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityVersionedId().getId());
        updateBgpSecIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityId());
        updateBgpSecIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateRevokedEvent(IncomingCertificateRevokedEvent event, CommandContext context) {
        // All BGPSec entities are already revoked and removed by the key pair deletion service in this case.
    }

    @Override
    public void updateBgpSecIfNeeded(ManagedCertificateAuthority ca) {
        var validated = validateBgpSecConfiguration(ca);
        if (!validated.toBeRevoked().isEmpty() || !validated.toBeIssued().isEmpty()) {
            log.debug("Revoking {} and issuing {} BGPSec entities", validated.toBeRevoked().size(), validated.toBeIssued().size());
        }

        for (BgpSecEntity bgpSecEntity : validated.toBeRevoked()) {
            bgpSecEntity.revokeAndRemove(bgpSecEntityRepository);
        }
        for (BgpSecConfiguration bgpSecConfiguration : validated.toBeIssued()) {
            createBgpSecEntity(ca, bgpSecConfiguration).ifPresent(bgpSecEntityRepository::add);
        }
    }

    private ValidatedBgpSec validateBgpSecConfiguration(ManagedCertificateAuthority ca) {
        Collection<BgpSecEntity> existing = bgpSecEntityRepository.findCurrentByCertificateAuthority(ca);
        List<BgpSecConfiguration> configured = bgpSecConfigurationRepository.findByCertificateAuthority(ca);

        Optional<IncomingResourceCertificate> maybeCurrentIncomingResourceCertificate = ca.findCurrentIncomingResourceCertificate();
        if (maybeCurrentIncomingResourceCertificate.isEmpty()) {
            // No current resource certificate, so all BGPSec entities are invalid and without resources there is
            // no applicable configuration
            return new ValidatedBgpSec(existing, Collections.emptyList());
        }

        var incomingResourceCertificate = maybeCurrentIncomingResourceCertificate.get();

        var configuredKeys = configured.stream().map(BgpSecKey::new).collect(Collectors.toUnmodifiableSet());
        var toBeRevoked = existing.stream()
            .map(x -> Pair.of(new BgpSecKey(x), x))
            .filter(entity ->
                !configuredKeys.contains(entity.getKey()) || !isValidBgpSecEntity(ca, incomingResourceCertificate, entity.getValue()))
                .reduce(
                        Pair.of(Set.<BgpSecKey>of(), Set.<BgpSecEntity>of()),
                        (acc, x) -> Pair.of(
                                Sets.union(acc.getLeft(), Set.of(x.getKey())),
                                Sets.union(acc.getRight(), Set.of(x.getValue()))
                        ),
                        (x, y) -> Pair.of(Sets.union(x.getLeft(), y.getLeft()), Sets.union(x.getRight(), y.getRight()))
                );
        // Produce a set of all object keys that are retained, so that any configuration whose object gets revoked, is reissued.
        var retainedKeys = Sets.difference(
            existing.stream().map(BgpSecKey::new).collect(Collectors.toUnmodifiableSet()),
            toBeRevoked.getLeft()
        );

        var toBeIssued = configured.stream().filter(bgpSecConf -> !retainedKeys.contains(new BgpSecKey(bgpSecConf))).toList();
        return new ValidatedBgpSec(toBeRevoked.getRight(), toBeIssued);
    }

    private record BgpSecKey(Asn asn, String keyIdentifier) {
        public BgpSecKey(BgpSecEntity entity) {
            this(entity.getAsn(), entity.getKeyIdentifier());
        }

        public BgpSecKey(BgpSecConfiguration configuration) {
            this(configuration.getAsn(), configuration.getKeyIdentifier());
        }
    }


    private boolean isValidBgpSecEntity(ManagedCertificateAuthority ca, IncomingResourceCertificate incomingResourceCertificate, BgpSecEntity bgpSec) {
        try {
            var certificate = bgpSec.getCertificate();
            var isValidAndCurrent = certificate.isValid()
                    && certificate.getSigningKeyPair().isCurrent()
                    && Objects.equals(incomingResourceCertificate.getPublicationUri(), certificate.getCertificate().getParentCertificateUri())
                    && incomingResourceCertificate.getCertifiedResources().contains(bgpSec.getAsn())
                    && Objects.equals(incomingResourceCertificate.getNotValidAfter(), certificate.getNotValidAfter());

            if (!isValidAndCurrent && log.isInfoEnabled()) {
                log.info("Will re-issue BGPSec at {} certificate-valid={} keypair-current={} parent-uri={} resources-match={}",
                    ca.getName(),
                    certificate.isValid(),
                    certificate.getSigningKeyPair().isCurrent(),
                    Objects.equals(incomingResourceCertificate.getPublicationUri(),
                        certificate.getCertificate().getParentCertificateUri()),
                    incomingResourceCertificate.getCertifiedResources().contains(bgpSec.getAsn())
                );
            }

            return isValidAndCurrent;
        } catch (UnparseableRpkiObjectException e) {
            log.warn("Will re-issue BGPSec at {} due to unparseable RPKI object: {}", ca.getName(), e.getMessage());
            return false;
        }
    }

    @VisibleForTesting
    Optional<BgpSecEntity> createBgpSecEntity(ManagedCertificateAuthority ca, BgpSecConfiguration bgpSecConfiguration) {
        if (!ca.getCertifiedResources().contains(bgpSecConfiguration.getAsn())) {
            return Optional.empty();
        }

        var now = DateTime.now(DateTimeZone.UTC);
        KeyPairEntity currentKeyPair = ca.getCurrentKeyPair();
        IncomingResourceCertificate incomingResourceCertificate = currentKeyPair.getCurrentIncomingCertificate();
        ValidityPeriod validityPeriod = new ValidityPeriod(now, incomingResourceCertificate.getNotValidAfter());

        var publicKey = Csr.getPublicKey(bgpSecConfiguration.getCsr());
        var bgpSecCertificate = certificateFactory.issueAndPersistBgpSecCertificate(
                requestBgpSecCertificate(bgpSecConfiguration.getAsn(), publicKey, bgpSecConfiguration.getRouterId()),
                bgpSecConfiguration.getAsn(), validityPeriod, currentKeyPair);

        var filename = informationAccessStrategy.bgpSecFilename(
                bgpSecCertificate.getCertificate(),
                bgpSecConfiguration.getAsn(),
                bgpSecConfiguration.getRouterId());
        var directory = CertificateInformationAccessUtil.extractPublicationDirectory(incomingResourceCertificate.getSia());

        return Optional.of(new BgpSecEntity(bgpSecConfiguration.getAsn(), bgpSecConfiguration.getKeyIdentifier(),
                bgpSecConfiguration.getRouterId(), bgpSecCertificate, filename, directory));
    }

    private CertificateIssuanceRequest requestBgpSecCertificate(Asn asn, PublicKey publicKey, Long routerId) {
        X500Principal subject = informationAccessStrategy.bgpSecCertificateSubject(asn, routerId);
        X509CertificateInformationAccessDescriptor[] sia = new X509CertificateInformationAccessDescriptor[]{};
        return new CertificateIssuanceRequest(ResourceExtension.ofResources(ImmutableResourceSet.of(asn)), subject, publicKey, sia);
    }

    record ValidatedBgpSec(Collection<BgpSecEntity> toBeRevoked,
                           Collection<BgpSecConfiguration> toBeIssued) {
    }


}
