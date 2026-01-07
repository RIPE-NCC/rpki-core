package net.ripe.rpki.publication.server;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.publication.api.PublicationMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalPublishingServerTest {

    private static final URI PUBLICATION_SERVER_URL = URI.create("https://localhost/publication-server");

    private static final Random RANDOM = new Random();

    private ExternalPublishingServer externalPublishingServer;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PublishingServerClient publishingServerClient = mock(PublishingServerClient.class);

    private final String replyDoesntMatter = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";

    @Before
    public void setUp() {
        externalPublishingServer = new ExternalPublishingServer(publishingServerClient, meterRegistry, PUBLICATION_SERVER_URL);
    }

    @Test
    public void shouldCreateEmptyRequest() {
        final String query = "<msg type=\"query\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"/>";
        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), clientIdCaptor.capture())).thenReturn(Mono.just(replyDoesntMatter));
        String clientId = getRandomClientId();
        externalPublishingServer.execute(Collections.emptyList(), clientId);
        assertEquals(query, xmlCaptor.getValue());
        assertEquals(clientId, clientIdCaptor.getValue());
    }

    @Test
    public void shouldCreateProperRequest() throws URISyntaxException {
        String query = "<msg type=\"query\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">" +
                "<publish uri=\"rsync://blabla.com/xxx.cer\">AQID</publish>" +
                "<withdraw hash=\"not important\" uri=\"rsync://blabla.com/yyy.cer\"/>" +
                "<publish hash=\"aHash\" uri=\"rsync://blabla.com/xxx.cer\">AQID</publish></msg>";

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), clientIdCaptor.capture())).thenReturn(Mono.just(replyDoesntMatter));
        List<PublicationMessage> messages = new ArrayList<>();
        messages.add(new PublicationMessage.PublishRequest(new URI("rsync://blabla.com/xxx.cer"), new byte[]{1, 2, 3}, Optional.empty()));
        messages.add(new PublicationMessage.WithdrawRequest(new URI("rsync://blabla.com/yyy.cer"), "not important"));
        messages.add(new PublicationMessage.PublishRequest(new URI("rsync://blabla.com/xxx.cer"), new byte[]{1, 2, 3}, java.util.Optional.of("aHash")));
        String clientId = getRandomClientId();
        externalPublishingServer.execute(messages, clientId);
        assertEquals(query, xmlCaptor.getValue());
        assertEquals(clientId, clientIdCaptor.getValue());
    }

    private String getRandomClientId() {
        return String.valueOf(RANDOM.nextInt());
    }

    @Test
    public void shouldParseEmptyResponse() {
        String reply = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), anyString())).thenReturn(Mono.just(reply));
        List<? extends PublicationMessage> parsedReply = externalPublishingServer.execute(Collections.emptyList(), getRandomClientId());
        assertTrue(parsedReply.isEmpty());
    }

    @Test
    public void shouldParseMeaningfulResponse() {
        String reply = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">" +
                "<publish uri=\"rsync://wombat.example/Alice/aaa.cer\"/>\n" +
                "<withdraw uri=\"rsync://wombat.example/Alice/bbb.cer\"/>\n" +
                "<report_error error_code=\"an_error_code\">Bla bla</report_error>\n" +
                "</msg>";
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), anyString())).thenReturn(Mono.just(reply));
        List<? extends PublicationMessage> parsedReply = externalPublishingServer.execute(Collections.emptyList(), getRandomClientId());
        assertEquals(3, parsedReply.size());
        PublicationMessage.PublishReply r1 = (PublicationMessage.PublishReply) parsedReply.get(0);
        PublicationMessage.WithdrawReply r2 = (PublicationMessage.WithdrawReply) parsedReply.get(1);
        PublicationMessage.ErrorReply err = (PublicationMessage.ErrorReply) parsedReply.get(2);
        assertEquals("rsync://wombat.example/Alice/aaa.cer", r1.getUri().toString());
        assertEquals("rsync://wombat.example/Alice/bbb.cer", r2.getUri().toString());
        assertEquals("an_error_code", err.getErrorCode());
        assertEquals("Bla bla", err.getMessage());
    }

    @Test
    public void should_create_list_request() {
        String query = "<msg type=\"query\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"><list/></msg>";

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), xmlCaptor.capture(), clientIdCaptor.capture())).thenReturn(Mono.just(replyDoesntMatter));
        List<? extends PublicationMessage> messages = Collections.singletonList(new PublicationMessage.ListRequest());
        String clientId = getRandomClientId();
        externalPublishingServer.execute(messages, clientId);
        assertEquals(query, xmlCaptor.getValue().replaceAll("[\\r\\n]", ""));
        assertEquals(clientId, clientIdCaptor.getValue());
    }

    @Test
    public void should_parse_list_response() {
        String reply = "<msg\n" +
                "        type=\"reply\"\n" +
                "        version=\"3\"\n" +
                "        xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\">\n" +
                "    <list uri=\"rsync://wombat.example/Alice/blCrcCp9ltyPDNzYKPfxc.cer\"\n" +
                "          hash=\"6D776A0A90EA55F479F63C15B3BFC8E91CFBEA549439CF9C474AAB738D741223\"/>\n" +
                "    <list uri=\"rsync://wombat.example/Alice/blCrcCp9ltyPDNzYKPfxc.mft\"\n" +
                "          hash=\"6D776A0A90EA55F479F63C15B3BFC8E91CFBEA549439CF9C474AAB738D741224\"/>\n" +
                "</msg>";
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), anyString())).thenReturn(Mono.just(reply));
        List<? extends PublicationMessage> parsedReply = externalPublishingServer.execute(Collections.emptyList(), getRandomClientId());
        assertEquals(2, parsedReply.size());
        PublicationMessage.ListReply r1 = (PublicationMessage.ListReply) parsedReply.get(0);
        PublicationMessage.ListReply r2 = (PublicationMessage.ListReply) parsedReply.get(1);
        assertEquals("rsync://wombat.example/Alice/blCrcCp9ltyPDNzYKPfxc.cer", r1.uri.toString());
        assertEquals("rsync://wombat.example/Alice/blCrcCp9ltyPDNzYKPfxc.mft", r2.uri.toString());
        assertEquals("6D776A0A90EA55F479F63C15B3BFC8E91CFBEA549439CF9C474AAB738D741223", r1.hash);
        assertEquals("6D776A0A90EA55F479F63C15B3BFC8E91CFBEA549439CF9C474AAB738D741224", r2.hash);
    }

    @Test
    public void should_update_publication_metrics() throws Exception {
        String clientId = RandomStringUtils.insecure().nextAlphanumeric(8);
        String reply = "<msg type=\"reply\" version=\"3\" xmlns=\"http://www.hactrn.net/uris/rpki/publication-spec/\"></msg>";
        when(publishingServerClient.publish(eq(PUBLICATION_SERVER_URL), anyString(), eq(clientId))).thenReturn(Mono.just(reply));
        List<? extends PublicationMessage> messages = Stream.of(
                new PublicationMessage.PublishRequest(new URI("rsync://blabla.com/xxx.cer"), new byte[]{1, 2, 3}, Optional.empty()),
                new PublicationMessage.PublishRequest(new URI("rsync://blabla.com/xxx2.cer"), new byte[]{1, 2, 3, 4}, Optional.empty()),
                new PublicationMessage.WithdrawRequest(new URI("rsync://blabla.com/yyy.cer"), "not important"),
                new PublicationMessage.PublishRequest(new URI("rsync://blabla.com/xxx.roa"), new byte[]{1, 2, 3}, Optional.of("aHash")),
                new PublicationMessage.WithdrawRequest(new URI("rsync://blabla.com/zzz.weird-extension"), "not important")
        ).toList();
        externalPublishingServer.execute(messages, clientId);
        assertEquals(2.0, meterRegistry.get("rpkicore.publication.operations").tag("operation", "publish").tag("type", "cer").counter().count(), 0.1);
        assertEquals(1.0, meterRegistry.get("rpkicore.publication.operations").tag("operation", "publish").tag("type", "roa").counter().count(), 0.1);
        assertEquals(1.0, meterRegistry.get("rpkicore.publication.operations").tag("operation", "withdraw").tag("type", "cer").counter().count(), 0.1);
        assertEquals(1.0, meterRegistry.get("rpkicore.publication.operations").tag("operation", "withdraw").tag("type", "unknown").counter().count(), 0.1);
    }

}
