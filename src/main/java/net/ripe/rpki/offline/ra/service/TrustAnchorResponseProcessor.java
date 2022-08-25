package net.ripe.rpki.offline.ra.service;

import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.EncodedPublicKey;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.TrustAnchorPublishedObject;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.OfflineResponseProcessorException;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.response.ErrorResponse;
import net.ripe.rpki.commons.ta.domain.response.RevocationResponse;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import net.ripe.rpki.commons.ta.domain.response.TaResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer that accepts uploaded response files (through web ui) and takes
 * care of further processing of the responses from the offline CA layer.
 * <p>
 * I.e. stuff like: - republishing the offline objects - notifying the
 * production CA about new certs / revoked keys
 */
@Component
public class TrustAnchorResponseProcessor {

    @PersistenceContext
    private EntityManager entityManager;

    private final X500Principal productionCaName;
    private final X500Principal allResourcesCaName;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private final KeyPairDeletionService keyPairDeletionService;
    private final ResourceCache resourceCache;

    @Inject
    public TrustAnchorResponseProcessor(@Value("${" + RepositoryConfiguration.ALL_RESOURCES_CA_NAME + "}") X500Principal allResourcesCaName,
                                        @Value("${" + RepositoryConfiguration.PRODUCTION_CA_NAME + "}") X500Principal productionCaName,
                                        CertificateAuthorityRepository certificateAuthorityRepository,
                                        PublishedObjectRepository publishedObjectRepository,
                                        TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
                                        KeyPairDeletionService keyPairDeletionService,
                                        ResourceCache resourceCache) {
        this.allResourcesCaName = allResourcesCaName;
        this.productionCaName = productionCaName;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.publishedObjectRepository = publishedObjectRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.resourceCache = resourceCache;
    }

    public void process(TrustAnchorResponse response) {
        resourceCache.verifyResourcesArePresent();
        validateResponse(response);

        List<String> revokedKeys = new ArrayList<>();
        for (TaResponse taResponse : response.getTaResponses()) {
            if (taResponse instanceof SigningResponse) {
                processTaSigningResponse((SigningResponse) taResponse);
            } else if (taResponse instanceof RevocationResponse) {
                RevocationResponse revokeResponse = (RevocationResponse) taResponse;
                processTrustAnchorRevocationResponse(revokeResponse);
                revokedKeys.add(revokeResponse.getEncodedPublicKey());
            } else if (taResponse instanceof ErrorResponse) {
                processTrustAnchorErrorResponse((ErrorResponse) taResponse);
            }
        }

        deleteRevokedKeysForAllResourcesCa(revokedKeys);
        publishTrustAnchorObjects(response.getPublishedObjects());

        removePendingRequest();
    }

    private void deleteRevokedKeysForAllResourcesCa(List<String> revokedKeys) {
        keyPairDeletionService.deleteRevokedKeys(getAllResourcesCa(), revokedKeys);
    }

    private void publishTrustAnchorObjects(Map<URI, CertificateRepositoryObject> objectsToPublish) {
        List<TrustAnchorPublishedObject> publishedObjects = applyChangeToPublishedObjects(objectsToPublish);
        trustAnchorPublishedObjectRepository.persist(publishedObjects);
    }

    List<TrustAnchorPublishedObject> applyChangeToPublishedObjects(Map<URI, CertificateRepositoryObject> objectsToPublish) {
        final ArrayList<TrustAnchorPublishedObject> result = new ArrayList<>();

        final Map<URI, TrustAnchorPublishedObject> activeObjects = convertToMap(trustAnchorPublishedObjectRepository.findActiveObjects());

        objectsToPublish.forEach((uri, objectToPublish) -> {
            if (activeObjects.containsKey(uri)) {
                final TrustAnchorPublishedObject publishedObject = activeObjects.remove(uri);
                if (!objectsAreSame(publishedObject, objectToPublish, uri)) {
                    publishedObject.withdraw();
                    result.add(publishedObject);
                    result.add(new TrustAnchorPublishedObject(uri, objectToPublish.getEncoded()));
                }
            } else {
                result.add(new TrustAnchorPublishedObject(uri, objectToPublish.getEncoded()));
            }
        });
        withdrawObjects(activeObjects.values());
        result.addAll(activeObjects.values());
        return result;
    }

    private boolean objectsAreSame(TrustAnchorPublishedObject publishedObject, CertificateRepositoryObject objectToPublish, URI uri) {
        return uri.equals(publishedObject.getUri()) && Arrays.equals(objectToPublish.getEncoded(), publishedObject.getContent());
    }

    private void withdrawObjects(Iterable<TrustAnchorPublishedObject> objects) {
        for (TrustAnchorPublishedObject object : objects) {
            object.withdraw(); // FIXME implicitly relying on JPA to persist this
        }
    }

    private Map<URI, TrustAnchorPublishedObject> convertToMap(List<TrustAnchorPublishedObject> publishedObjects) {
        return publishedObjects.stream()
                .collect(Collectors.toMap(TrustAnchorPublishedObject::getUri,
                        publishedObject -> publishedObject,
                        (a, b) -> b,
                        () -> new HashMap<>(publishedObjects.size())));
    }

    private void processTaSigningResponse(SigningResponse signingResponse) {
        CertificateIssuanceResponse response = CertificateIssuanceResponse.fromTaSigningResponse(signingResponse);
        getAllResourcesCa().processCertificateIssuanceResponse(response, null);
    }


    private void processTrustAnchorRevocationResponse(RevocationResponse revocationResponse) {
        getAllResourcesCa().processRevokedKey(revocationResponse.getEncodedPublicKey(), publishedObjectRepository);
    }

    private void processTrustAnchorErrorResponse(ErrorResponse errorResponse) {
        AllResourcesCertificateAuthority allResourcesCa = getAllResourcesCa();

        TaRequest request = findCorrespondingTaRequest(errorResponse, allResourcesCa);
        if (request instanceof SigningRequest) {
            SigningRequest signingRequest = (SigningRequest) request;
            final EncodedPublicKey encodedPublicKey = new EncodedPublicKey(signingRequest.getResourceCertificateRequest().getEncodedSubjectPublicKey());
            allResourcesCa.findKeyPairByPublicKey(encodedPublicKey).ifPresent(keyPairEntity -> {
                if (keyPairEntity.isNew()) {
                    allResourcesCa.removeKeyPair(keyPairEntity);
                }
            });
        }
    }

    private TaRequest findCorrespondingTaRequest(ErrorResponse errorResponse, AllResourcesCertificateAuthority productionCa) {
        return productionCa.getUpStreamCARequestEntity().getUpStreamCARequest().getTaRequests().stream()
            .filter(existing -> existing.getRequestId().equals(errorResponse.getRequestId()))
            .findFirst()
            .orElse(null);
    }


    private void removePendingRequest() {
        // Remove the existing request entity.
        // I tried all possible permutation of @Cascade on the field in
        // CertificateAuthority (most likely first), but no success that way :(
        AllResourcesCertificateAuthority allResourcesCa = getAllResourcesCa();
        UpStreamCARequestEntity upStreamCARequestEntity = allResourcesCa.getUpStreamCARequestEntity();
        allResourcesCa.setUpStreamCARequestEntity(null);
        entityManager.remove(upStreamCARequestEntity);
        entityManager.flush();
    }

    private void validateResponse(TrustAnchorResponse response) {
        HostedCertificateAuthority productionCa = getAllResourcesCa();
        UpStreamCARequestEntity upStreamCaRequestEntity = productionCa.getUpStreamCARequestEntity();

        validatePendingRequestExists(upStreamCaRequestEntity);
        validateResponseContainsTrustAnchorResponse(response);
        validateRequestAndResponseHaveSameTimeStamp(response, upStreamCaRequestEntity);
    }

    private void validateResponseContainsTrustAnchorResponse(TrustAnchorResponse response) {
        if (response.getTaResponses() == null) {
            throw new OfflineResponseProcessorException("Trust Anchor response did not contain actual response!");
        }
    }

    private void validatePendingRequestExists(UpStreamCARequestEntity upStreamCARequestEntity) {
        if (upStreamCARequestEntity == null) {
            throw new OfflineResponseProcessorException("Can't find pending request");
        }
    }

    private void validateRequestAndResponseHaveSameTimeStamp(TrustAnchorResponse response, UpStreamCARequestEntity upStreamCARequestEntity) {
        long creationTimeMillis = upStreamCARequestEntity.getUpStreamCARequest().getCreationTimestamp();
        long correspondingRequestCreationTimeStamp = response.getRequestCreationTimestamp();

        if (creationTimeMillis != correspondingRequestCreationTimeStamp) {
            throw new OfflineResponseProcessorException("Response seems related to request dated: "
                + new DateTime(correspondingRequestCreationTimeStamp) + ", but current pending request is dated: "
                + new DateTime(creationTimeMillis));
        }
    }

    private AllResourcesCertificateAuthority getAllResourcesCa() {
        return certificateAuthorityRepository.findAllresourcesCAByName(allResourcesCaName);
    }

    /**
     * For unit testing only
     */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
