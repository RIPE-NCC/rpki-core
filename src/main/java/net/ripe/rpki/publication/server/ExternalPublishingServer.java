package net.ripe.rpki.publication.server;

import com.jamesmurty.utils.XMLBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.publication.api.PublicationMessage;
import net.ripe.rpki.publication.api.PublicationMessage.ErrorReply;
import net.ripe.rpki.publication.api.PublicationMessage.ListReply;
import net.ripe.rpki.publication.api.PublicationMessage.ListRequest;
import net.ripe.rpki.publication.api.PublicationMessage.PublishReply;
import net.ripe.rpki.publication.api.PublicationMessage.PublishRequest;
import net.ripe.rpki.publication.api.PublicationMessage.WithdrawReply;
import net.ripe.rpki.publication.api.PublicationMessage.WithdrawRequest;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.TransformerException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ExternalPublishingServer {

    private static final String OP_TAG_NAME_PUBLISH = "publish";
    private static final String OP_TAG_NAME_WITHDRAW = "withdraw";
    private static final String OP_TAG_NAME_LIST = "list";
    private static final String OP_TAG_NAME_REPORT_ERROR = "report_error";

    private final PublishingServerClient publishingServerClient;

    @Getter
    private final URI publishingServerUrl;

    private final Counter rrdpPublicationSuccesses;
    private final Counter rrdpPublicationFailures;
    private final Counter rrdpPublishes;
    private final Counter rrdpWithdraws;
    private final Counter rrdpDataSent;
    private final AtomicInteger rrdpParallelPublishes;
    private final Timer successfulPublishTime;
    private final Timer failedPublishTime;

    public ExternalPublishingServer(
        PublishingServerClient publishingServerClient,
        MeterRegistry meterRegistry,
        URI publishingServerUrl) {
        this.publishingServerClient = publishingServerClient;
        this.publishingServerUrl = publishingServerUrl;

        rrdpPublicationSuccesses = Counter.builder("rpkicore.publication")
            .description("The number of publication messages successfully sent to RRDP repository")
            .tag("status", "success")
            .tag("publication", "rrdp")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpPublicationFailures = Counter.builder("rpkicore.publication")
            .description("The number of publication messages failed to be sent to RRDP repository")
            .tag("status", "failure")
            .tag("publication", "rrdp")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpPublishes = Counter.builder("rpkicore.publication.operations")
            .description("The number of publish messages sent to RRDP repository")
            .tag("publication", "rrdp")
            .tag("operation", "publish")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);
        rrdpWithdraws = Counter.builder("rpkicore.publication.operations")
            .description("The number of withdraw messages sent to RRDP repository")
            .tag("publication", "rrdp")
            .tag("operation", "withdraw")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpDataSent = Counter.builder("rpkicore.publication.total.payload.size")
            .description("Amount of data that is sent to the publication server, in bytes")
            .tag("publication", "rrdp")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpParallelPublishes = new AtomicInteger(0);

        Gauge.builder("rpkicore.publication.parallel.publications", rrdpParallelPublishes::get)
            .description("Number of parallel publications at the moment")
            .tag("publication", "rrdp")
            .tag("uri", publishingServerUrl.toString())
            .register(meterRegistry);

        successfulPublishTime = createTimer(meterRegistry, "success");
        failedPublishTime = createTimer(meterRegistry, "failure");
    }

    private static Timer createTimer(MeterRegistry meterRegistry, String status) {
        return Timer.builder("rpkicore.publication.request.duration")
            .tag("status", status)
            .description("Time for publication HTTP request")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(4))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(meterRegistry);
    }

    public List<? extends PublicationMessage> execute(List<? extends PublicationMessage> messages, String clientId) {
        if (publishingServerClient == null) {
            log.warn("Publishing server client is not properly initialized.");
            return Collections.emptyList();
        }
        final StringBuilder logMessage = new StringBuilder("Sending to publishing server [");
        logMessage.append(publishingServerUrl).append("] using clientId=").append(clientId).append(":\n");

        try {
            final XMLBuilder xml = XMLBuilder
                    .create("msg", "http://www.hactrn.net/uris/rpki/publication-spec/")
                    .a("version", "3")
                    .a("type", "query");
            for (PublicationMessage publicationMessage : messages) {
                if (publicationMessage instanceof PublishRequest) {
                    final PublishRequest publish = (PublishRequest) publicationMessage;
                    final XMLBuilder elem = xml.e(OP_TAG_NAME_PUBLISH).a("uri", publish.getUri().toString());
                    publish.hashToReplace.ifPresent(s -> elem.a("hash", s));
                    elem.t(publish.getBase64Content());
                    logMessage.append('\t').append(publish).append('\n');
                    rrdpPublishes.increment(1);
                } else if (publicationMessage instanceof WithdrawRequest) {
                    final WithdrawRequest withdraw = (WithdrawRequest) publicationMessage;
                    xml.e(OP_TAG_NAME_WITHDRAW).a("uri", withdraw.getUri().toString()).a("hash", withdraw.hash);
                    logMessage.append('\t').append(withdraw).append('\n');
                    rrdpWithdraws.increment(1);
                } else if (publicationMessage instanceof ListRequest) {
                    xml.e(OP_TAG_NAME_LIST);
                    logMessage.append('\t').append(publicationMessage).append('\n');
                }
            }

            log.info(logMessage.toString());
            String xmlRequest = xml.asString();

            rrdpDataSent.increment(xmlRequest.getBytes(StandardCharsets.UTF_8).length);

            rrdpParallelPublishes.incrementAndGet();
            boolean succeeded = false;

            final String postResponse;
            long begin = System.nanoTime();
            try {
                postResponse = publishingServerClient.publish(publishingServerUrl, xmlRequest, clientId).block();
                succeeded = true;
            } finally {
                long end = System.nanoTime();
                final Duration duration = Duration.ofNanos(end - begin);
                if (succeeded) {
                    successfulPublishTime.record(duration);
                } else {
                    failedPublishTime.record(duration);
                }
                rrdpParallelPublishes.decrementAndGet();
            }

            log.debug("Parsing the publishing server response");
            return incrementCounters(parseResponse(postResponse));
        } catch (ParserConfigurationException | TransformerException | XMLStreamException | URISyntaxException e) {
            // consider all messages failed
            rrdpPublicationFailures.increment(messages.size());
            throw new RuntimeException(e);
        } catch (Throwable t) {
            rrdpPublicationFailures.increment(messages.size());
            throw t;
        }
    }

    private List<? extends PublicationMessage> incrementCounters(List<? extends PublicationMessage> replies) {
        final long failureCount = replies.stream()
            .filter(reply -> reply instanceof ErrorReply)
            .count();
        rrdpPublicationFailures.increment(failureCount);
        rrdpPublicationSuccesses.increment(replies.size() - failureCount);
        return replies;
    }

    private List<? extends PublicationMessage> parseResponse(String response) throws XMLStreamException, URISyntaxException {
        final List<PublicationMessage> replies = new ArrayList<>();
        final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(new StringReader(response));
        String errorCode = null;
        final QName uriAttrName = new QName("uri");
        final QName hashAttrName = new QName("hash");
        final QName errorAttrName = new QName("error_code");

        while (reader.hasNext()) {
            final XMLEvent event = reader.nextEvent();
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    StartElement startElement = event.asStartElement();
                    if (OP_TAG_NAME_PUBLISH.equals(startElement.getName().getLocalPart())) {
                        final String uri = startElement.getAttributeByName(uriAttrName).getValue();
                        replies.add(new PublishReply(new URI(uri)));
                    } else if (OP_TAG_NAME_WITHDRAW.equals(startElement.getName().getLocalPart())) {
                        final String uri = startElement.getAttributeByName(uriAttrName).getValue();
                        replies.add(new WithdrawReply(new URI(uri)));
                    } else if (OP_TAG_NAME_LIST.equals(startElement.getName().getLocalPart())) {
                        final String uri = startElement.getAttributeByName(uriAttrName).getValue();
                        final String hash = startElement.getAttributeByName(hashAttrName).getValue();
                        replies.add(new ListReply(new URI(uri), hash));
                    } else if (OP_TAG_NAME_REPORT_ERROR.equals(startElement.getName().getLocalPart())) {
                        errorCode = startElement.getAttributeByName(errorAttrName).getValue();
                    }
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    EndElement endElement = event.asEndElement();
                    if (OP_TAG_NAME_REPORT_ERROR.equals(endElement.getName().getLocalPart())) {
                        errorCode = null;
                    }
                    break;

                case XMLStreamConstants.CHARACTERS:
                    Characters characters = event.asCharacters();
                    final String tagContent = characters.getData().trim();
                    if (errorCode != null) {
                        replies.add(new ErrorReply(errorCode, tagContent));
                    }
                    break;
            }
        }
        return replies;
    }

}
