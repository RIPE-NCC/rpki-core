package net.ripe.rpki.offline.ra.service;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.EncodedPublicKey;
import net.ripe.rpki.commons.crypto.util.SignedObjectUtil;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
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
import org.joda.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
@Slf4j
@Component
public class TrustAnchorResponseProcessor {

    @PersistenceContext
    private EntityManager entityManager;

    private final X500Principal allResourcesCaName;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    private final KeyPairDeletionService keyPairDeletionService;
    private final ResourceCache resourceCache;

    @Inject
    public TrustAnchorResponseProcessor(@Value("${" + RepositoryConfiguration.ALL_RESOURCES_CA_NAME + "}") X500Principal allResourcesCaName,
                                        CertificateAuthorityRepository certificateAuthorityRepository,
                                        TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository,
                                        KeyPairDeletionService keyPairDeletionService,
                                        ResourceCache resourceCache) {
        this.allResourcesCaName = allResourcesCaName;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.trustAnchorPublishedObjectRepository = trustAnchorPublishedObjectRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.resourceCache = resourceCache;
    }

    public void process(TrustAnchorResponse response) {
        resourceCache.verifyResourcesArePresent();
        AllResourcesCertificateAuthority allResourcesCa = getAllResourcesCa();
        validateResponse(allResourcesCa, response);

        for (TaResponse taResponse : response.getTaResponses()) {
            if (taResponse instanceof SigningResponse) {
                processTaSigningResponse(allResourcesCa, (SigningResponse) taResponse);
            } else if (taResponse instanceof RevocationResponse) {
                processTrustAnchorRevocationResponse(allResourcesCa, (RevocationResponse) taResponse);
            } else if (taResponse instanceof ErrorResponse) {
                processTrustAnchorErrorResponse(allResourcesCa, (ErrorResponse) taResponse);
            }
        }

        publishTrustAnchorObjects(response.getPublishedObjects());

        removePendingRequest(allResourcesCa);
    }

    private void publishTrustAnchorObjects(Map<URI, CertificateRepositoryObject> objectsToPublish) {
        List<TrustAnchorPublishedObject> publishedObjects = applyChangeToPublishedObjects(objectsToPublish);
        trustAnchorPublishedObjectRepository.persist(publishedObjects);
    }

    List<TrustAnchorPublishedObject> applyChangeToPublishedObjects(Map<URI, CertificateRepositoryObject> objectsToPublish) {
        final ArrayList<TrustAnchorPublishedObject> result = new ArrayList<>();

        final Map<URI, TrustAnchorPublishedObject> activeObjects = convertToMap(trustAnchorPublishedObjectRepository.findActiveObjects());
        // Use the same time as fallback for all files in a request
        var now = Instant.now();

        objectsToPublish.forEach((uri, objectToPublish) -> {
            Instant creationTime;
            try {
                creationTime = SignedObjectUtil.getFileCreationTime(uri, objectToPublish.getEncoded());
            } catch (SignedObjectUtil.NoTimeParsedException e) {
                log.error("Could not determine creation time for object: " + uri, e);
                creationTime = now;
            }

            if (activeObjects.containsKey(uri)) {
                final TrustAnchorPublishedObject publishedObject = activeObjects.remove(uri);
                if (!objectsAreSame(publishedObject, objectToPublish, uri)) {
                    publishedObject.withdraw();
                    result.add(publishedObject);
                    result.add(new TrustAnchorPublishedObject(uri, objectToPublish.getEncoded(), creationTime));
                }
            } else {
                result.add(new TrustAnchorPublishedObject(uri, objectToPublish.getEncoded(), creationTime));
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
            object.withdraw();
        }
    }

    private Map<URI, TrustAnchorPublishedObject> convertToMap(List<TrustAnchorPublishedObject> publishedObjects) {
        return publishedObjects.stream()
                .collect(Collectors.toMap(TrustAnchorPublishedObject::getUri,
                        publishedObject -> publishedObject,
                        (a, b) -> b,
                        () -> new HashMap<>(publishedObjects.size())));
    }

    private void processTaSigningResponse(AllResourcesCertificateAuthority allResourcesCa, SigningResponse signingResponse) {
        CertificateIssuanceResponse response = CertificateIssuanceResponse.fromTaSigningResponse(signingResponse);
        allResourcesCa.processCertificateIssuanceResponse(response, null);
    }


    private void processTrustAnchorRevocationResponse(AllResourcesCertificateAuthority allResourcesCa, RevocationResponse revocationResponse) {
        String encodedSKI = revocationResponse.getEncodedPublicKey();
        final KeyPairEntity kp = allResourcesCa.findKeyPairByEncodedPublicKey(encodedSKI)
            .orElseThrow(() -> new CertificateAuthorityException("Unknown encoded key: " + encodedSKI));
        allResourcesCa.processCertificateRevocationResponse(
            new CertificateRevocationResponse(kp.getPublicKey()),
            keyPairDeletionService
        );
    }

    private void processTrustAnchorErrorResponse(AllResourcesCertificateAuthority allResourcesCa, ErrorResponse errorResponse) {
        TaRequest request = findCorrespondingTaRequest(errorResponse, allResourcesCa);
        if (request instanceof SigningRequest) {
            SigningRequest signingRequest = (SigningRequest) request;
            final EncodedPublicKey encodedPublicKey = new EncodedPublicKey(signingRequest.getResourceCertificateRequest().getEncodedSubjectPublicKey());
            allResourcesCa.findKeyPairByPublicKey(encodedPublicKey).ifPresent(keyPairEntity -> {
                if (keyPairEntity.isPending() && keyPairEntity.findCurrentIncomingCertificate().isEmpty()) {
                    allResourcesCa.removeKeyPair(keyPairEntity);
                }
            });
        }
    }

    private TaRequest findCorrespondingTaRequest(ErrorResponse errorResponse, AllResourcesCertificateAuthority allResourcesCa) {
        return allResourcesCa.getUpStreamCARequestEntity().getUpStreamCARequest().getTaRequests().stream()
            .filter(existing -> existing.getRequestId().equals(errorResponse.getRequestId()))
            .findFirst()
            .orElse(null);
    }


    private void removePendingRequest(AllResourcesCertificateAuthority allResourcesCa) {
        // Remove the existing request entity.
        // I tried all possible permutation of @Cascade on the field in
        // CertificateAuthority (most likely first), but no success that way :(
        UpStreamCARequestEntity upStreamCARequestEntity = allResourcesCa.getUpStreamCARequestEntity();
        allResourcesCa.setUpStreamCARequestEntity(null);
        entityManager.remove(upStreamCARequestEntity);
        entityManager.flush();
    }

    private void validateResponse(AllResourcesCertificateAuthority allResourcesCa, TrustAnchorResponse response) {
        UpStreamCARequestEntity upStreamCaRequestEntity = allResourcesCa.getUpStreamCARequestEntity();

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
        return certificateAuthorityRepository.findAllResourcesCAByName(allResourcesCaName);
    }

    /**
     * For unit testing only
     */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
