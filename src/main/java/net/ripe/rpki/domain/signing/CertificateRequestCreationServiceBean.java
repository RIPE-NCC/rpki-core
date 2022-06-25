package net.ripe.rpki.domain.signing;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.request.ResourceCertificateRequestData;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.ResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.apache.commons.lang3.Validate;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Slf4j
@Service
public class CertificateRequestCreationServiceBean implements CertificateRequestCreationService {

    private final RepositoryConfiguration configuration;

    public CertificateRequestCreationServiceBean(RepositoryConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Initiate Key Roll, IF:
     * = there is only key
     * = and this key is current
     * = and it is the same age or older than the maxage
     *
     * Returns empty list of signing requests if nothing happened
     */
    @Override
    public CertificateIssuanceRequest initiateKeyRoll(int maxAge,
                                                      KeyPairService keyPairService,
                                                      HostedCertificateAuthority ca) {
        if (ca.hasCurrentKeyPair() && !ca.hasRollInProgress() && ca.currentKeyPairIsOlder(maxAge)) {
            KeyPairEntity kp = ca.createNewKeyPair(keyPairService);
            try {

                // Request certificate for it with same resources as before
                final IpResourceSet resources = ca.getCurrentIncomingCertificate().getResources();
                final X509CertificateInformationAccessDescriptor[] sia = getSubjectInformationAccessDescriptors(kp, ca, DEFAULT_RESOURCE_CLASS);
                final X500Principal dn = deriveSubjectDN(kp.getPublicKey(), null);
                return new CertificateIssuanceRequest(resources, dn, kp.getPublicKey(), sia);
            } finally {
                kp.unloadKeyPair();
            }
        }
        return null;
    }

    @Override
    public List<CertificateIssuanceRequest> createCertificateIssuanceRequestForAllKeys(HostedCertificateAuthority ca, IpResourceSet certifiableResources) {
        final List<CertificateIssuanceRequest> requests = new ArrayList<>();
        for (KeyPairEntity kp : ca.getKeyPairs()) {
            if (kp.isCertificateNeeded()) {
                try {
                    final Optional<IncomingResourceCertificate> currentIncomingCertificate = kp.findCurrentIncomingCertificate();
                    final X509ResourceCertificate existingCertificate = currentIncomingCertificate.map(ResourceCertificate::getCertificate).orElse(null);
                    X500Principal dn = deriveSubjectDN(kp.getPublicKey(), existingCertificate);
                    X509CertificateInformationAccessDescriptor[] sia = getSubjectInformationAccessDescriptors(kp, ca, DEFAULT_RESOURCE_CLASS);
                    CertificateIssuanceRequest signRequest = new CertificateIssuanceRequest(certifiableResources, dn, kp.getPublicKey(), sia);
                    requests.add(signRequest);
                } finally {
                    kp.unloadKeyPair();
                }
            }
        }
        return requests;
    }

    @Override
    public List<SigningRequest> requestProductionCertificates(IpResourceSet certifiableResources,
                                                              HostedCertificateAuthority ca) {
        List<SigningRequest> requests = new ArrayList<>();
        for (KeyPairEntity kp : ca.getKeyPairs()) {
            if (kp.isCertificateNeeded()) {
                final Optional<IncomingResourceCertificate> currentIncomingCertificate = kp.findCurrentIncomingCertificate();
                final Boolean needToRequest = currentIncomingCertificate.map(currentCertificate ->
                    notificationUriChanged(currentCertificate, configuration.getNotificationUri())
                        || publicRepositoryUriChanged(currentCertificate, configuration.getPublicRepositoryUri())
                        || resourcesChanged(currentCertificate, certifiableResources, DEFAULT_RESOURCE_CLASS)
                        || newValidityTimeAppliesForProductionCertificate(currentCertificate, DEFAULT_RESOURCE_CLASS))
                    .orElse(false);

                if (currentCertificateIsNull(currentIncomingCertificate, DEFAULT_RESOURCE_CLASS) || needToRequest) {
                    try {
                        final X509ResourceCertificate existingCertificate = currentIncomingCertificate.map(ResourceCertificate::getCertificate).orElse(null);
                        X500Principal dn = deriveSubjectDN(kp.getPublicKey(), existingCertificate);
                        X509CertificateInformationAccessDescriptor[] sia = getSubjectInformationAccessDescriptors(kp, ca, DEFAULT_RESOURCE_CLASS);
                        ResourceCertificateRequestData resourceCertificateRequestData = ResourceCertificateRequestData.forUpstreamCARequest(DEFAULT_RESOURCE_CLASS, dn, kp.getPublicKey(), sia, certifiableResources);
                        requests.add(new SigningRequest(resourceCertificateRequestData));
                    } finally {
                        kp.unloadKeyPair();
                    }
                }
            }
        }
        return requests;
    }

    @Override
    public List<CertificateRevocationRequest> createCertificateRevocationRequestForAllKeys(HostedCertificateAuthority ca) {
        return ca.getKeyPairs().stream()
            .map(keyPair -> {
                try {
                    return new CertificateRevocationRequest(keyPair.getPublicKey());
                } finally {
                    keyPair.unloadKeyPair();
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public CertificateRevocationRequest createCertificateRevocationRequestForOldKey(HostedCertificateAuthority ca) {
        Optional<KeyPairEntity> key = ca.findOldKeyPair();
        Validate.isTrue(key.isPresent(), "Cannot find an OLD key pair");
        try {
            return new CertificateRevocationRequest(key.get().getPublicKey());
        } finally {
            key.get().unloadKeyPair();
        }
    }

    @Override
    public TrustAnchorRequest createTrustAnchorRequest(List<TaRequest> signingRequests) {
        URI notificationUri = configuration.getNotificationUri();
        URI repositoryUri = configuration.getPublicRepositoryUri();
        List<X509CertificateInformationAccessDescriptor> descriptors = Lists.newArrayList();
        if (notificationUri != null) {
            descriptors.add(new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY, notificationUri));
        }
        if (repositoryUri != null) {
            descriptors.add(new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, repositoryUri));
        }

        return new TrustAnchorRequest(
            configuration.getTrustAnchorRepositoryUri(),
            descriptors.toArray(new X509CertificateInformationAccessDescriptor[0]),
            signingRequests);
    }

    private boolean publicRepositoryUriChanged(IncomingResourceCertificate currentCertificate, URI publicRepositoryUri) {
        Validate.notNull(publicRepositoryUri, "publicRepositoryUri can't be null");
        if (currentCertificate.getSia() != null) {
            for (X509CertificateInformationAccessDescriptor descriptor : currentCertificate.getSia()) {
                if (X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY.equals(descriptor.getMethod())) {
                    return !descriptor.getLocation().toString().startsWith(publicRepositoryUri.toString());
                }
            }
        }
        return true;
    }

    private boolean notificationUriChanged(IncomingResourceCertificate currentCertificate, URI notificationUri) {
        if (currentCertificate.getSia() != null) {
            for (X509CertificateInformationAccessDescriptor descriptor : currentCertificate.getSia()) {
                if (X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY.equals(descriptor.getMethod())) {
                    return !descriptor.getLocation().equals(notificationUri);
                }
            }
        }
        return notificationUri != null;
    }

    public X509CertificateInformationAccessDescriptor[] siaForCaCertificate(KeyPairEntity kp) {
        return kp.findCurrentIncomingCertificate()
            .map(ResourceCertificate::getSia)
            .orElse(
                new X509CertificateInformationAccessDescriptor[]{
                    new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                        configuration.getPublicRepositoryUri()),
                    new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                        configuration.getPublicRepositoryUri().resolve(kp.getManifestFilename())),
                }
            );
    }

    private X509CertificateInformationAccessDescriptor[] getSubjectInformationAccessDescriptors(KeyPairEntity kp,
                                                                                                HostedCertificateAuthority ca,
                                                                                                String resourceClassName) {
        Map<ASN1ObjectIdentifier, X509CertificateInformationAccessDescriptor> sias = new LinkedHashMap<>();

        // Not sure why we first copy the existing SIA entries and then overwrite them later. Is there ever a case
        // where the certificate contains additional entries that need to be preserved, except for non-hosted CAs
        // where we just accept the requested SIA anyway?
        for (X509CertificateInformationAccessDescriptor accessDescriptor : siaForCaCertificate(kp)) {
            X509CertificateInformationAccessDescriptor previousValue = sias.put(accessDescriptor.getMethod(), accessDescriptor);
            Validate.isTrue(previousValue == null, "duplicate SIA access methods entries are not supported");
        }

        ResourceCertificateInformationAccessStrategy accessStrategy = new ResourceCertificateInformationAccessStrategyBean();
        URI certificateRepositoryLocation = accessStrategy.defaultCertificateRepositoryLocation(ca, resourceClassName);

        URI notificationUri = configuration.getNotificationUri();
        if (notificationUri != null) {
            sias.put(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY,
                    new X509CertificateInformationAccessDescriptor(
                            X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY,
                            notificationUri));
        } else {
            sias.remove(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY);
        }

        URI publicRepositoryUri = configuration.getPublicRepositoryUri();
        URI publicationDirectory = publicRepositoryUri.resolve(certificateRepositoryLocation);
        sias.put(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                new X509CertificateInformationAccessDescriptor(
                        X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                        publicationDirectory));

        sias.put(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                new X509CertificateInformationAccessDescriptor(
                        X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                        publicationDirectory.resolve(kp.getManifestFilename())));

        return sias.values().toArray(new X509CertificateInformationAccessDescriptor[0]);
    }

    private X500Principal deriveSubjectDN(PublicKey publicKey, X509ResourceCertificate existingCertificate) {
        X500Principal subjectDN;
        if (existingCertificate == null) {
            ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
            subjectDN = ias.caCertificateSubject(publicKey);
        } else {
            subjectDN = existingCertificate.getSubject();
        }
        return subjectDN;
    }

    private boolean currentCertificateIsNull(Optional<IncomingResourceCertificate> currentCertificate, String name) {
        if (!currentCertificate.isPresent()) {
            log.info("No current certificate for resource class " + name + ", requesting new certificate");
            return true;
        }
        return false;
    }

    private boolean resourcesChanged(IncomingResourceCertificate currentCertificate, IpResourceSet certifiableResources, String name) {
        if (!currentCertificate.getResources().equals(certifiableResources)) {
            log.info("Current certificate for resource class " + name + ", has different resources. Was: " + currentCertificate.getResources() + ", will request: " + certifiableResources);
            return true;
        }
        return false;
    }

    private boolean newValidityTimeAppliesForProductionCertificate(IncomingResourceCertificate currentCertificate, String name) {
        if (currentCertificate.getValidityPeriod().getNotValidAfter().isBefore(new DateTime().plusYears(5))) {
            log.info("Current certificate for resource class " + name + " expires less than 5 years from now, requesting new certificate");
            return true;
        }
        return false;
    }
}
