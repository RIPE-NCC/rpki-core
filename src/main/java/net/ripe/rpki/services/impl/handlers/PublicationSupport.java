package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.publication.api.PublicationMessage;
import net.ripe.rpki.publication.api.PublicationMessage.ListReply;
import net.ripe.rpki.publication.api.PublicationMessage.ListRequest;
import net.ripe.rpki.publication.api.PublicationMessage.WithdrawRequest;
import net.ripe.rpki.publication.server.ExternalPublishingServer;
import net.ripe.rpki.publication.server.PublishingServerClient;
import net.ripe.rpki.util.Streams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Publishes signed material for a CA, using the publication service.
 *
 * Extracted because publication may happen as the result of a publication command, or in the context
 * of another command such as a key roll over, key revocation and subsequent archival in particular.
 *
 * The plan is to use asynchronous messages for this in future. However, we need to refactor the domain quite
 * a bit before this is feasible.
 */
@Component
@Slf4j
public class PublicationSupport {

    public static final String CORE_CLIENT_ID = "RIPE_NCC_CORE";

    private final List<ExternalPublishingServer> externalPublishingServers;
    private final ForkJoinPool forkJoinPool;

    @Inject
    public PublicationSupport(
        PublishingServerClient publishingServerClient,
        MeterRegistry meterRegistry,
        @Value("${publication.server.url}") List<URI> publicationServerUris
    ) {
        log.info("Interfacing with {} external publication servers: {}", publicationServerUris.size(), publicationServerUris);

        externalPublishingServers = publicationServerUris.stream()
            .map(uri -> new ExternalPublishingServer(publishingServerClient, meterRegistry, uri))
            .collect(Collectors.toList());
        forkJoinPool = new ForkJoinPool(Math.max(1, externalPublishingServers.size()));
    }

    public void publishAllObjects(List<PublishedObjectData> publishedObjects) {
        forkJoinPool.submit(() -> externalPublishingServers.parallelStream().forEach(externalPublishingServer -> {
            try {
                publishObjects(externalPublishingServer, publishedObjects, CORE_CLIENT_ID);
            } catch (Exception e) {
                log.error("Publication to external publication server {} failed:", externalPublishingServer.getPublishingServerUrl(), e);
            }
        })).join();
    }

    private void publishObjects(ExternalPublishingServer externalPublishingServer, List<PublishedObjectData> publishedObjects, String clientId) {
        final Map<URI, PublishedObjectData> localObjects = publishedObjects.stream().collect(
            Collectors.toMap(PublishedObjectData::getUri, po -> po)
        );
        log.info("Publishing {} active objects to {} for client {}", localObjects.size(), externalPublishingServer.getPublishingServerUrl(), clientId);

        final List<PublicationMessage.ListReply> objectOnServer = getObjectsFromServer(externalPublishingServer, clientId);
        Map<URI, ListReply> theirObjects = groupPublicationMessagesByUri(objectOnServer);
        log.info("Received {} ({} unique URIs) objects from server for client {}", objectOnServer.size(), theirObjects.size(), clientId);

        List<PublicationMessage> resolutionMessages = getResolutionMessages(theirObjects, localObjects);
        if (resolutionMessages.isEmpty()) {
            return;
        }
        log.info("Sending {} publish/replace/withdraw operations for client {}", resolutionMessages.size(), clientId);

        List<? extends PublicationMessage> publishResults = externalPublishingServer.execute(resolutionMessages, clientId);
        if (publishResults.stream().anyMatch(PublicationMessage.isErrorReply)) {
            final Stream<? extends PublicationMessage> errorReplies =
                    publishResults.stream().filter(PublicationMessage.isErrorReply);
            logErrors(errorReplies);
        }
    }

    private void logErrors(Stream<? extends PublicationMessage> errorReplies) {
        Streams.grouped(errorReplies, 1000).forEach(bucket ->
                log.error("Got errors from the publication server:\n" + Joiner.on('\n').join(bucket)));
    }

    private static List<PublicationMessage> getResolutionMessages(Map<URI, ListReply> remoteObjects, Map<URI, PublishedObjectData> localObjects) {
        List<PublicationMessage> result = new ArrayList<>();

        Set<URI> localUris = localObjects.keySet();
        Set<URI> remoteUris = remoteObjects.keySet();

        Set<URI> onlyRemote = new HashSet<>(remoteUris);
        onlyRemote.removeAll(localUris);

        Set<URI> onlyLocal = new HashSet<>(localUris);
        onlyLocal.removeAll(remoteUris);

        Set<URI> existBoth = new HashSet<>(localUris);
        existBoth.retainAll(remoteUris);

        // URIs only on remote need to all be withdrawn.
        for (URI toWithdraw : onlyRemote) {
            String hash = remoteObjects.get(toWithdraw).hash;
            result.add(new WithdrawRequest(toWithdraw, hash));
        }

        // URIs found only on local have to be republished if the last updated publishing object is publishable.
        for (URI toRepublish : onlyLocal) {
            PublishedObjectData latest = localObjects.get(toRepublish);
            // Not replacing remote objects, we are only publishing new ones, so the hash must be empty.
            result.add(new PublicationMessage.PublishRequest(latest.getUri(), latest.getContent(), Optional.empty()));
        }

        // URIs found on both has might need to be withdrawn, or leave as is.
        for (URI onBoth : existBoth) {
            PublishedObjectData local = localObjects.get(onBoth);
            ListReply remote = remoteObjects.get(onBoth);
            String localObjectHash = objectHash(local.getContent());
            if (!remote.hash.equalsIgnoreCase(localObjectHash)) {
                result.add(new PublicationMessage.PublishRequest(local.getUri(), local.getContent(), Optional.of(remote.hash)));
            } else {
                // All good, local and published hash match.
            }
        }

        return result;
    }

    private Map<URI, ListReply> groupPublicationMessagesByUri(List<ListReply> publicationMessages) {
        return Maps.uniqueIndex(publicationMessages, input -> input.uri);
    }

    private List<ListReply> getObjectsFromServer(ExternalPublishingServer externalPublishingServer, String clientId) {
        final List<ListRequest> messages = Collections.singletonList(new ListRequest());
        return externalPublishingServer.execute(messages, clientId).stream()
                .filter(PublicationMessage.isListReply)
                .map(x -> (ListReply) x)
                .collect(Collectors.toList());
    }

    public static String objectHash(byte[] bytes) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return BaseEncoding.base16().encode(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
