package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import junit.framework.TestCase;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.publication.server.PublishingServerClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.domain.CertificationDomainTestCase.BASE_URI;
import static net.ripe.rpki.services.impl.handlers.PublicationSupport.CORE_CLIENT_ID;
import static net.ripe.rpki.services.impl.handlers.PublicationSupport.objectHash;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PublicationSupportTest extends TestCase {

    private static final String LIST_REQUEST = "<msg xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\" type=\"query\" version=\"3\"><list/></msg>";

    private static final URI PUBLICATION_SERVER_URL = URI.create("https://localhost/publication-server");

    @Mock
    private PublishedObjectRepository publishedObjectRepository;

    @Mock
    private PublishingServerClient publishingServerClient;

    @Captor
    private ArgumentCaptor<String> xmlCaptor;

    private PublicationSupport subject;
    private PublishedObjectData published1;
    private PublishedObjectData published2;

    @Before
    public void setUp() throws SecurityException, URISyntaxException {
        published1 = new PublishedObjectData(new Timestamp(System.currentTimeMillis()), BASE_URI.resolve("object.cer"), new byte[]{4, 5, 6});
        published2 = new PublishedObjectData(new Timestamp(System.currentTimeMillis()), BASE_URI.resolve("manifest.mft"), new byte[]{1, 2, 3});

        when(publishedObjectRepository.findCurrentlyPublishedObjects()).thenReturn(Arrays.asList(published1, published2));

        subject = new PublicationSupport(publishingServerClient, new SimpleMeterRegistry(), Collections.singletonList(PUBLICATION_SERVER_URL));
    }

    @Test
    public void should_publish_all_objects_when_no_remote_objects_present() {
        final String listResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";
        final String publishResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";

        when(publishingServerClient.publish(PUBLICATION_SERVER_URL, LIST_REQUEST, CORE_CLIENT_ID)).thenReturn(listResponse);
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), eq(CORE_CLIENT_ID))).thenReturn(publishResponse);

        subject.publishAllObjects(Arrays.asList(published1, published2));

        verify(publishingServerClient, times(2)).publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), eq(CORE_CLIENT_ID));
        List<String> xmlRequests = xmlCaptor.getAllValues();
        assertEquals(LIST_REQUEST, xmlRequests.get(0));
        assertEquals("<msg xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\" type=\"query\" version=\"3\">" +
            "<publish uri=\"rsync://localhost:20873/repository/manifest.mft\">AQID</publish>" +
            "<publish uri=\"rsync://localhost:20873/repository/object.cer\">BAUG</publish>" +
            "</msg>", xmlRequests.get(1));
    }

    @Test
    public void should_withdraw_objects_not_in_local_objects() {
        final String listResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">" +
            "<list uri=\"" + published2.getUri() +"\" hash=\"" + objectHash(published2.getContent()) + "\"/>" +
            "</msg>";
        final String publishResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";

        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), eq(CORE_CLIENT_ID))).thenReturn(listResponse).thenReturn(publishResponse);

        subject.publishAllObjects(Collections.singletonList(published1));

        verify(publishingServerClient, times(2)).publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), eq(CORE_CLIENT_ID));
        List<String> xmlRequests = xmlCaptor.getAllValues();
        assertEquals(LIST_REQUEST, xmlRequests.get(0));
        assertEquals("<msg xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\" type=\"query\" version=\"3\">" +
            "<withdraw hash=\"039058C6F2C0CB492C533B0A4D14EF77CC0F78ABCCCED5287D84A1A2011CFB81\" uri=\"rsync://localhost:20873/repository/manifest.mft\"/>" +
            "<publish uri=\"rsync://localhost:20873/repository/object.cer\">BAUG</publish>" +
            "</msg>", xmlRequests.get(1));
    }

    @Test
    public void should_replace_objects_when_hash_does_not_match() {
        final String listResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">" +
            "<list uri=\"" + published2.getUri() +"\" hash=\"01234\"/>" +
            "</msg>";
        final String publishResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";

        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), eq(CORE_CLIENT_ID))).thenReturn(listResponse).thenReturn(publishResponse);

        subject.publishAllObjects(Arrays.asList(published1, published2));

        verify(publishingServerClient, times(2)).publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), eq(CORE_CLIENT_ID));
        List<String> xmlRequests = xmlCaptor.getAllValues();
        assertEquals(LIST_REQUEST, xmlRequests.get(0));
        assertEquals("<msg xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\" type=\"query\" version=\"3\">" +
            "<publish uri=\"rsync://localhost:20873/repository/object.cer\">BAUG</publish>" +
            "<publish hash=\"01234\" uri=\"rsync://localhost:20873/repository/manifest.mft\">AQID</publish>" +
            "</msg>", xmlRequests.get(1));
    }

    @Test
    public void should_keep_objects_with_matching_hash() {
        final String listResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">" +
            "<list uri=\"" + published2.getUri() +"\" hash=\"" + objectHash(published2.getContent()) + "\"/>" +
            "</msg>";
        final String publishResponse = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";

        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), eq(CORE_CLIENT_ID))).thenReturn(listResponse).thenReturn(publishResponse);

        subject.publishAllObjects(Arrays.asList(published1, published2));

        verify(publishingServerClient, times(2)).publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), eq(CORE_CLIENT_ID));
        List<String> xmlRequests = xmlCaptor.getAllValues();
        assertEquals(LIST_REQUEST, xmlRequests.get(0));
        assertEquals("<msg xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\" type=\"query\" version=\"3\">" +
            "<publish uri=\"rsync://localhost:20873/repository/object.cer\">BAUG</publish>" +
            "</msg>", xmlRequests.get(1));
    }

    @Test
    public void should_compute_correct_hash() {
        final byte[] bytes = "sample text".getBytes(StandardCharsets.US_ASCII);
        // echo -n "sample text" | openssl dgst -sha256 -hex | tr a-f A-F
        assertEquals("BC658C641EF71739FB9995BDED59B21150BBFF4367F6E4E4C7934B489B9D2C00", objectHash(bytes));
    }
}
