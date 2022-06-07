package net.ripe.rpki.publication.server;

import com.jamesmurty.utils.XMLBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
public class ExternalPublishingServer {

    private static final String OP_TAG_NAME_PUBLISH = "publish";
    private static final String OP_TAG_NAME_WITHDRAW = "withdraw";
    private static final String OP_TAG_NAME_LIST = "list";
    private static final String OP_TAG_NAME_REPORT_ERROR = "report_error";

    private static final String METRIC_TAG_STATUS = "status";
    private static final String METRIC_TAG_PUBLICATION = "publication";
    private static final String METRIC_TAG_URI = "uri";

    private final PublishingServerClient publishingServerClient;

    @Getter
    private final URI publishingServerUrl;

    private final Counter rrdpPublicationSuccesses;
    private final Counter rrdpPublicationFailures;
    private final Counter rrdpDataSent;
    private final AtomicInteger rrdpParallelPublishes;
    private final Timer successfulPublishTime;
    private final Timer failedPublishTime;

    private final Map<ObjectType, Counter> rrdpPublishMap = new ConcurrentHashMap<>();
    private final Map<ObjectType, Counter> rrdpWithdrawMap = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    public ExternalPublishingServer(
        PublishingServerClient publishingServerClient,
        MeterRegistry meterRegistry,
        URI publishingServerUrl) {

        this.meterRegistry = meterRegistry;
        this.publishingServerClient = publishingServerClient;
        this.publishingServerUrl = publishingServerUrl;

        rrdpPublicationSuccesses = Counter.builder("rpkicore.publication")
            .description("The number of publication messages successfully sent to RRDP repository")
            .tag(METRIC_TAG_STATUS, "success")
            .tag(METRIC_TAG_PUBLICATION, "rrdp")
            .tag(METRIC_TAG_URI, publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpPublicationFailures = Counter.builder("rpkicore.publication")
            .description("The number of publication messages failed to be sent to RRDP repository")
            .tag(METRIC_TAG_STATUS, "failure")
            .tag(METRIC_TAG_PUBLICATION, "rrdp")
            .tag(METRIC_TAG_URI, publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpDataSent = Counter.builder("rpkicore.publication.total.payload.size")
            .description("Amount of data that is sent to the publication server, in bytes")
            .tag(METRIC_TAG_PUBLICATION, "rrdp")
            .tag(METRIC_TAG_URI, publishingServerUrl.toString())
            .register(meterRegistry);

        rrdpParallelPublishes = new AtomicInteger(0);

        Gauge.builder("rpkicore.publication.parallel.publications", rrdpParallelPublishes::get)
            .description("Number of parallel publications at the moment")
            .tag(METRIC_TAG_PUBLICATION, "rrdp")
            .tag(METRIC_TAG_URI, publishingServerUrl.toString())
            .register(meterRegistry);

        successfulPublishTime = createTimer(meterRegistry, "success");
        failedPublishTime = createTimer(meterRegistry, "failure");
    }

    private Counter createWithdrawCounter(MeterRegistry meterRegistry, ObjectType objectType) {
        return createCounter(meterRegistry, OP_TAG_NAME_WITHDRAW, objectType,
            "The number of withdraw messages sent to RRDP repository");
    }

    private Counter createPublishCounter(MeterRegistry meterRegistry, ObjectType objectType) {
        return createCounter(meterRegistry, OP_TAG_NAME_PUBLISH, objectType,
            "The number of publish messages sent to RRDP repository");
    }

    private Counter createCounter(MeterRegistry meterRegistry, String operation, ObjectType objectType, String description) {
        return Counter.builder("rpkicore.publication.operations")
            .description(description)
            .tag(METRIC_TAG_PUBLICATION, "rrdp")
            .tag("operation", operation)
            .tag("type", objectType.getFileExtension())
            .tag("uri", this.publishingServerUrl.toString())
            .register(meterRegistry);
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
                    oneMorePublish(publish.getUri());
                } else if (publicationMessage instanceof WithdrawRequest) {
                    final WithdrawRequest withdraw = (WithdrawRequest) publicationMessage;
                    xml.e(OP_TAG_NAME_WITHDRAW).a("uri", withdraw.getUri().toString()).a("hash", withdraw.hash);
                    logMessage.append('\t').append(withdraw).append('\n');
                    oneMoreWithdraw(withdraw.getUri());
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

    private void oneMorePublish(URI uri) {
        bumpCounter(uri, rrdpPublishMap, objectType -> createPublishCounter(meterRegistry, objectType));
    }

    private void oneMoreWithdraw(URI uri) {
        bumpCounter(uri, rrdpWithdrawMap, objectType -> createWithdrawCounter(meterRegistry, objectType));
    }

    private void bumpCounter(URI uri, Map<ObjectType, Counter> map, Function<ObjectType, Counter> createCounter) {
        final ObjectType objectType = ObjectType.find(uri.getPath());
        if (objectType == ObjectType.Unknown) {
            log.info("Unknown file extension on: {}", uri);
        }
        map.compute(objectType, (ot, counter) -> {
            if (counter == null) {
                counter = createCounter.apply(objectType);
            }
            counter.increment(1);
            return counter;
        });
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

    @AllArgsConstructor
    public enum ObjectType {
        Mft("mft"),
        Roa("roa"),
        Cer("cer"),
        Crl("crl"),
        Gbr("gbr"),
        Aspa("asa"),
        Unknown("unknown");

        @Getter
        private final String fileExtension;

        public static ObjectType find(String name) {
            return Arrays.stream(values())
                .filter(t -> name.endsWith("." + t.getFileExtension()))
                .findFirst()
                .orElse(Unknown);
        }
    }

}
