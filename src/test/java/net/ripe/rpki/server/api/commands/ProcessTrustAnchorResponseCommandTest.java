package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.ta.domain.response.ErrorResponse;
import net.ripe.rpki.commons.ta.domain.response.RevocationResponse;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import net.ripe.rpki.commons.ta.domain.response.TaResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import org.joda.time.DateTime;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ProcessTrustAnchorResponseCommandTest {

    private ProcessTrustAnchorResponseCommand subject;

    @Test
    public void shouldHaveDescriptiveLogEntry() {
        Map<URI, CertificateRepositoryObject> publishedObjects = new HashMap<>();
        publishedObjects.put(URI.create("rsync://rpki.ripe.net/repository/foo.mft"), mock(CertificateRepositoryObject.class));

        List<TaResponse> taResponses = Arrays.asList(
                new SigningResponse(UUID.randomUUID(), "RIPE NCC", URI.create("rsync://rpki.ripe.net/repository/foo.cer"), mock(X509ResourceCertificate.class)),
                new RevocationResponse(UUID.randomUUID(), "RIPE NCC", "encoded_public_key"),
                new ErrorResponse(UUID.randomUUID(), "request already processed")
        );
        TrustAnchorResponse response = new TrustAnchorResponse(new DateTime(2013, 12, 31, 11, 44, 0, 0).getMillis(), publishedObjects, taResponses);

        subject = new ProcessTrustAnchorResponseCommand(new VersionedId(1), response);

        assertEquals("Process Trust Anchor response file with 3 response(s).\n" +
                "Response #1: (Re-)Issue certificate at location rsync://rpki.ripe.net/repository/foo.cer\n" +
                "Response #2: Revocation Notification for public key 'encoded_public_key' for resource class 'RIPE NCC'.\n" +
                "Response #3: Trust anchor failed to process this request. Reason: request already processed",
                subject.getCommandSummary());
    }

    @Test
    public void shouldHaveDescriptiveLogEntryForRepublishResponse() {
        Map<URI, CertificateRepositoryObject> publishedObjects = new HashMap<>();
        publishedObjects.put(URI.create("rsync://rpki.ripe.net/repository/foo.mft"), mock(CertificateRepositoryObject.class));
        TrustAnchorResponse response = new TrustAnchorResponse(new DateTime(2013, 12, 31, 11, 44, 0, 0).getMillis(), publishedObjects, Collections.emptyList());

        subject = new ProcessTrustAnchorResponseCommand(new VersionedId(1), response);

        assertEquals("Process Trust Anchor response file with Republish Request Response containing TA objects.", subject.getCommandSummary());
    }
}
