package net.ripe.rpki.domain.roa;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsBuilder;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.IncomingCertificateRevokedEvent;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.SingleUseEeCertificateFactory;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.naming.RepositoryObjectNamingStrategy;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.services.command.UnparseableRpkiObjectException;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;

@Service("roaEntityServiceBean")
@Slf4j
public class RoaEntityServiceBean implements CertificateAuthorityEventVisitor, RoaEntityService {

    private final RoaConfigurationRepository roaConfigurationRepository;

    private final RoaEntityRepository repository;

    private final ResourceCertificateInformationAccessStrategy informationAccessStrategy = new ResourceCertificateInformationAccessStrategyBean();

    private final CertificateAuthorityRepository certificateAuthorityRepository;

    private final SingleUseKeyPairFactory singleUseKeyPairFactory;

    private final SingleUseEeCertificateFactory singleUseEeCertificateFactory;

    @Autowired
    public RoaEntityServiceBean(CertificateAuthorityRepository certificateAuthorityRepository,
                                RoaConfigurationRepository roaConfigurationRepository,
                                RoaEntityRepository repository,
                                SingleUseKeyPairFactory singleUseKeyPairFactory,
                                SingleUseEeCertificateFactory singleUseEeCertificateFactory) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.repository = repository;
        this.singleUseKeyPairFactory = singleUseKeyPairFactory;
        this.singleUseEeCertificateFactory = singleUseEeCertificateFactory;
    }

    @Override
    public void visitKeyPairActivatedEvent(KeyPairActivatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityId());
        updateRoasIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityId());
        updateRoasIfNeeded(ca);
    }

    @Override
    public void visitIncomingCertificateRevokedEvent(IncomingCertificateRevokedEvent event, CommandContext context) {
        // All ROA entities are already revoked and removed by the key pair deletion service in this case.
    }

    /**
     * Validates the current ROA entities against the ROA configuration and incoming resources.
     *
     * @return a pair with ROA entities that are no longer valid and ROA specifications that are not satisfied
     * by the current resource certificate and configuration. When both lists are empty the ROA entities
     * are fully up-to-date with the ROA configuration.
     */
    public Pair<List<RoaEntity>, List<RoaSpecification>> validateRoaConfiguration(ManagedCertificateAuthority ca) {
        List<RoaEntity> roaEntities = repository.findCurrentByCertificateAuthority(ca);
        Optional<IncomingResourceCertificate> maybeCurrentIncomingResourceCertificate = ca.findCurrentIncomingResourceCertificate();
        if (!maybeCurrentIncomingResourceCertificate.isPresent()) {
            // No current resource certificate, so all ROA entities are invalid and without resources there is
            // no applicable configuration
            return Pair.of(roaEntities, Collections.emptyList());
        }

        IncomingResourceCertificate incomingResourceCertificate = maybeCurrentIncomingResourceCertificate.get();

        RoaConfiguration configuration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);
        Map<Asn, RoaSpecification> specifications = configuration.toRoaSpecifications(incomingResourceCertificate);
        Map<Boolean, List<RoaEntity>> validatedRoas = roaEntities.stream()
            .collect(Collectors.partitioningBy(roa -> isValidRoaEntity(incomingResourceCertificate, specifications, roa)));

        return Pair.of(
            validatedRoas.get(false),
            getUnsatisfiedSpecifications(validatedRoas.get(true), specifications)
        );
    }

    @Override
    public void updateRoasIfNeeded(ManagedCertificateAuthority ca) {
        Pair<List<RoaEntity>, List<RoaSpecification>> validated = validateRoaConfiguration(ca);
        if (!validated.getLeft().isEmpty() || !validated.getRight().isEmpty()) {
            log.debug("revoking {} and issuing {} ROA entities", validated.getLeft().size(), validated.getRight().size());
        }

        for (RoaEntity roaEntity : validated.getLeft()) {
            roaEntity.revokeAndRemove(repository);
        }
        for (RoaSpecification specification : validated.getRight()) {
            createRoaEntity(ca, specification);
        }
    }

    private boolean isValidRoaEntity(IncomingResourceCertificate incomingResourceCertificate, Map<Asn, RoaSpecification> specifications, RoaEntity roa) {
        try {
            RoaCms roaCms = roa.getRoaCms();
            RoaSpecification specification = specifications.get(roaCms.getAsn());

            return roa.getCertificate().isValid()
                && roa.getCertificate().getSigningKeyPair().isCurrent()
                && Objects.equals(incomingResourceCertificate.getPublicationUri(), roaCms.getParentCertificateUri())
                && specification != null
                && specification.isSatisfiedBy(roaCms);
        } catch (UnparseableRpkiObjectException e) {
            return false;
        }
    }

    private List<RoaSpecification> getUnsatisfiedSpecifications(List<RoaEntity> validRoas, Map<Asn, RoaSpecification> specifications) {
        Map<Asn, List<RoaEntity>> validRoasByAsn = validRoas.stream().collect(Collectors.groupingBy(RoaEntity::getAsn));
        return specifications.values().stream()
                .filter(specification -> isUnsatisfiedSpecification(validRoasByAsn, specification)).toList();
    }

    private boolean isUnsatisfiedSpecification(Map<Asn, List<RoaEntity>> validRoasByAsn, RoaSpecification specification) {
        return validRoasByAsn.getOrDefault(specification.getAsn(), Collections.emptyList()).stream()
            .noneMatch(roa -> specification.isSatisfiedBy(roa.getRoaCms()));
    }

    private void createRoaEntity(ManagedCertificateAuthority ca, RoaSpecification specification) {
        if (!specification.hasResources()) {
            return;
        }

        ValidityPeriod roaValidityPeriod = specification.calculateValidityPeriod();
        if (roaValidityPeriod == null) {
            return;
        }

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        OutgoingResourceCertificate endEntityCertificate = createEndEntityCertificateForRoa(specification, roaValidityPeriod, eeKeyPair, ca.getCurrentKeyPair());

        RoaCms roaCms = generateRoaCms(specification, eeKeyPair, endEntityCertificate.getCertificate());
        URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(
                ca.getCurrentIncomingCertificate().getSia());
        RoaEntity roaEntity = new RoaEntity(endEntityCertificate, roaCms,
                informationAccessStrategy.roaFilename(endEntityCertificate), publicationDirectory);
        repository.add(roaEntity);
    }

    private OutgoingResourceCertificate createEndEntityCertificateForRoa(RoaSpecification specification,
                                                                         ValidityPeriod roaValidityPeriod, KeyPair eeKeyPair, KeyPairEntity signingKeyPair) {
        CertificateIssuanceRequest request = requestForRoaEeCertificate(specification.getNormalisedResources(), signingKeyPair, eeKeyPair);
        return singleUseEeCertificateFactory.issueSingleUseEeResourceCertificate(
            request, roaValidityPeriod, signingKeyPair);
    }

    private RoaCms generateRoaCms(RoaSpecification specification, KeyPair eeKeyPair, X509ResourceCertificate endEntityX509ResourceCertificate) {
        RoaCmsBuilder roaCmsBuilder = new RoaCmsBuilder();
        roaCmsBuilder.withCertificate(endEntityX509ResourceCertificate);
        roaCmsBuilder.withAsn(specification.getAsn());
        roaCmsBuilder.withPrefixes(specification.calculatePrefixes());
        roaCmsBuilder.withSignatureProvider(singleUseKeyPairFactory.signatureProvider());
        return roaCmsBuilder.build(eeKeyPair.getPrivate());
    }

    private CertificateIssuanceRequest requestForRoaEeCertificate(ImmutableResourceSet resources, KeyPairEntity signingKeyPair, KeyPair eeKeyPair) {
        X500Principal subject = informationAccessStrategy.eeCertificateSubject(eeKeyPair.getPublic());
        X509CertificateInformationAccessDescriptor[] sia = informationAccessStrategy.siaForSignedObjectCertificate(signingKeyPair,
                RepositoryObjectNamingStrategy.ROA_FILE_EXTENSION, subject, eeKeyPair.getPublic());
        return new CertificateIssuanceRequest(ResourceExtension.ofResources(resources), subject, eeKeyPair.getPublic(), sia);
    }
}
