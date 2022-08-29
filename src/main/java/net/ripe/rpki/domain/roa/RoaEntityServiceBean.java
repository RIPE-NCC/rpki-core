package net.ripe.rpki.domain.roa;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsBuilder;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsParser;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.core.events.CertificateAuthorityEventVisitor;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.naming.RepositoryObjectNamingStrategy;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.CommandContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.net.URI;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service("roaEntityServiceBean")
public class RoaEntityServiceBean implements CertificateAuthorityEventVisitor, RoaEntityService {

    private final RoaConfigurationRepository roaConfigurationRepository;

    private final RoaEntityRepository repository;

    private final ResourceCertificateInformationAccessStrategy informationAccessStrategy = new ResourceCertificateInformationAccessStrategyBean();

    private final CertificateAuthorityRepository certificateAuthorityRepository;

    private final SingleUseKeyPairFactory singleUseKeyPairFactory;

    private final CertificateManagementService certificateManagementService;

    @Autowired
    public RoaEntityServiceBean(CertificateAuthorityRepository certificateAuthorityRepository,
                                RoaConfigurationRepository roaConfigurationRepository,
                                RoaEntityRepository repository,
                                SingleUseKeyPairFactory singleUseKeyPairFactory,
                                CertificateManagementService certificateManagementService) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.repository = repository;
        this.singleUseKeyPairFactory = singleUseKeyPairFactory;
        this.certificateManagementService = certificateManagementService;
    }

    @Override
    public void visitKeyPairActivatedEvent(KeyPairActivatedEvent event, CommandContext context) {
        ManagedCertificateAuthority ca = certificateAuthorityRepository.findManagedCa(event.getCertificateAuthorityVersionedId().getId());
        if (ca == null) {
            return;
        }

        revokeRoasSignedByOldKeys(ca);
    }

    private void revokeRoasSignedByOldKeys(ManagedCertificateAuthority ca) {
        ca.getKeyPairs().stream()
                .filter(KeyPairEntity::isOld)
                .flatMap(keyPair -> repository.findByCertificateSigningKeyPair(keyPair).stream())
                .filter(roa -> !roa.isRevoked())
                .forEachOrdered(roa -> {
                    roa.revoke();
                    repository.remove(roa);
                });
    }

    @Override
    public void updateRoasIfNeeded(ManagedCertificateAuthority ca) {
        final RoaConfiguration configuration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);
        ca.findCurrentKeyPair()
            .ifPresent(currentKeyPair ->
                currentKeyPair.findCurrentIncomingCertificate()
                    .ifPresent(currentIncomingCertificate -> {
                        Map<Asn, RoaSpecification> roaSpecifications = configuration.toRoaSpecifications(currentIncomingCertificate);

                        List<RoaEntity> roas = new ArrayList<>(repository.findByCertificateSigningKeyPair(currentKeyPair));

                        revokeAndRemoveUnparsableRoas(roas);
                        roas.removeIf(roa -> !roa.getCertificate().isValid());
                        revokeRoasIfParentCertificateLocationChanged(currentIncomingCertificate, roas);

                        ListMultimap<Asn, RoaEntity> roasByAsn = groupRoasByAsn(roas);
                        updateRoaEntitiesToMatchSpecifications(roaSpecifications, roasByAsn, currentKeyPair);
                        revokeInvalidatedRoas(roaSpecifications, roasByAsn.asMap());
                    }));
    }

    // TODO: WTF is this and why do we validate our own ROAs?
    private void revokeAndRemoveUnparsableRoas(List<RoaEntity> roas) {
        for (Iterator<RoaEntity> it = roas.iterator(); it.hasNext();) {
            RoaEntity roa = it.next();
            if (roa.getCertificate().isValid()) {
                RoaCmsParser parser = new RoaCmsParser();
                parser.parse("roa", roa.getPublishedObject().getContent());
                if (!parser.isSuccess()) {
                    roa.revoke();
                    it.remove();
                }
            }
        }
    }

    private void revokeRoasIfParentCertificateLocationChanged(IncomingResourceCertificate incomingResourceCertificate, List<RoaEntity> roas) {
        for (Iterator<RoaEntity> it = roas.iterator(); it.hasNext();) {
            RoaEntity roa = it.next();
            if (!incomingResourceCertificate.getPublicationUri().equals(roa.getRoaCms().getParentCertificateUri())) {
                roa.revoke();
                repository.remove(roa);
                it.remove();
            }
        }
    }

    private ListMultimap<Asn, RoaEntity> groupRoasByAsn(List<RoaEntity> roas) {
        ListMultimap<Asn, RoaEntity> result = ArrayListMultimap.create();
        for (RoaEntity roa : roas) {
            result.get(roa.getAsn()).add(roa);
        }
        return result;
    }


    private void updateRoaEntitiesToMatchSpecifications(Map<Asn, RoaSpecification> roaSpecifications, ListMultimap<Asn, RoaEntity> roasByAsn, KeyPairEntity currentKeyPair) {
        for (RoaSpecification specification : roaSpecifications.values()) {
            List<RoaEntity> roasForAsn = roasByAsn.get(specification.getAsn());
            updateRoasForAsn(specification, roasForAsn, currentKeyPair);
        }
    }

    private void createRoaEntity(RoaSpecification specification, KeyPairEntity currentKeyPair) {
        if (!specification.hasResources()) {
            return;
        }

        ValidityPeriod roaValidityPeriod = specification.calculateValidityPeriod();
        if (roaValidityPeriod == null) {
            return;
        }

        KeyPair eeKeyPair = singleUseKeyPairFactory.get();
        OutgoingResourceCertificate endEntityCertificate = createEndEntityCertificateForRoa(specification, roaValidityPeriod, eeKeyPair, currentKeyPair);

        RoaCms roaCms = generateRoaCms(specification, eeKeyPair, endEntityCertificate.getCertificate());
        URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(
                currentKeyPair.getCurrentIncomingCertificate().getSia());
        RoaEntity roaEntity = new RoaEntity(endEntityCertificate, roaCms,
                informationAccessStrategy.roaFilename(endEntityCertificate), publicationDirectory);
        repository.add(roaEntity);
    }

    private OutgoingResourceCertificate createEndEntityCertificateForRoa(RoaSpecification specification,
                                                                         ValidityPeriod roaValidityPeriod, KeyPair eeKeyPair, KeyPairEntity signingKeyPair) {
        ManagedCertificateAuthority certificateAuthority = specification.getCertificateAuthority();
        CertificateIssuanceRequest request = requestForRoaEeCertificate(specification.getNormalisedResources(), signingKeyPair, eeKeyPair, specification);
        return certificateManagementService.issueSingleUseEeResourceCertificate(
                certificateAuthority, request, roaValidityPeriod, signingKeyPair);
    }

    private RoaCms generateRoaCms(RoaSpecification specification, KeyPair eeKeyPair, X509ResourceCertificate endEntityX509ResourceCertificate) {
        RoaCmsBuilder roaCmsBuilder = new RoaCmsBuilder();
        roaCmsBuilder.withCertificate(endEntityX509ResourceCertificate);
        roaCmsBuilder.withAsn(specification.getAsn());
        roaCmsBuilder.withPrefixes(specification.calculatePrefixes());
        roaCmsBuilder.withSignatureProvider(singleUseKeyPairFactory.signatureProvider());
        return roaCmsBuilder.build(eeKeyPair.getPrivate());
    }

    private CertificateIssuanceRequest requestForRoaEeCertificate(IpResourceSet resources, KeyPairEntity signingKeyPair, KeyPair eeKeyPair, RoaSpecification specification) {
        X500Principal subject = informationAccessStrategy.eeCertificateSubject("ROA-" + specification.getAsn().toString(), eeKeyPair.getPublic(),
                signingKeyPair);
        X509CertificateInformationAccessDescriptor[] sia = informationAccessStrategy.siaForSignedObjectCertificate(signingKeyPair,
                RepositoryObjectNamingStrategy.ROA_FILE_EXTENSION, subject, eeKeyPair.getPublic());
        return new CertificateIssuanceRequest(resources, subject, eeKeyPair.getPublic(), sia);
    }

    private void updateRoasForAsn(RoaSpecification specification, List<RoaEntity> roasForAsn, KeyPairEntity currentKeyPair) {
        if (!existsRoaThatSatisfiedSpecification(specification, roasForAsn)) {
            for (RoaEntity roa : roasForAsn) {
                roa.revoke();
                repository.remove(roa);
            }
            createRoaEntity(specification, currentKeyPair);
        }
    }

    private void revokeInvalidatedRoas(Map<Asn, RoaSpecification> roaSpecifications, Map<Asn, Collection<RoaEntity>> roasByAsn) {
        roasByAsn.values().stream()
                .flatMap(Collection::stream)
                .forEachOrdered(roa -> {
                    final RoaCms roaCms = roa.getRoaCms();
                    final RoaSpecification specification = roaSpecifications.get(roaCms.getAsn());
                    if (specification == null || !specification.allows(roaCms)) {
                        roa.revoke();
                        repository.remove(roa);
                    }
                });
    }

    private boolean existsRoaThatSatisfiedSpecification(RoaSpecification specification, List<RoaEntity> roas) {
        return roas.stream().anyMatch(roa -> specification.isSatisfiedBy(roa.getRoaCms()));
    }

    @Transactional
    @Override
    public void logRoaPrefixDeletion(RoaConfiguration configuration, Collection<? extends RoaConfigurationPrefix> deletedPrefixes) {
        roaConfigurationRepository.logRoaPrefixDeletion(configuration, deletedPrefixes);
    }
}
