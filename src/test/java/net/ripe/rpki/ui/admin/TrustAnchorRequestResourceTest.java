package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorRequestSerializer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.wicket.util.resource.IResourceStreamWriter;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class TrustAnchorRequestResourceTest {

    private TrustAnchorRequestResource subject;

    private TrustAnchorRequest request;

    private TrustAnchorRequestSerializer serializer;

    @Before
    public void setUp() {
        serializer = new TrustAnchorRequestSerializer();
        request = serializer.deserialize(xml);
        subject = new TrustAnchorRequestResource(request);
    }

    @Test
    public void shouldHaveXmlMimeType() {
        assertEquals("application/xml", subject.getResourceStream().getContentType());
    }

    @Test
    public void shouldHaveFileNameWithXmlExtension() {
        assertTrue(subject.getFileName().endsWith(".xml"));
    }

    @Test
    public void shouldDeserializeTheResourceBackToRequest() throws Exception {
        IResourceStreamWriter sr = (IResourceStreamWriter) subject.getResourceStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        sr.write(output);

        TrustAnchorRequest result = serializer.deserialize(output.toString("UTF-8"));
        // Deserialized request must be identical to the original request. Due to
        // lack of a proper equals implementation on TrustAnchorRequest, make
        // a comparison based on reflection
        assertTrue(EqualsBuilder.reflectionEquals(request, result));
    }

    private final String xml = "<requests.TrustAnchorRequest>\n" +
            "  <creationTimestamp>1610359575105</creationTimestamp>\n" +
            "  <taCertificatePublicationUri>rsync://localhost:10873/ta/</taCertificatePublicationUri>\n" +
            "  <taRequests>\n" +
            "    <requests.RevocationRequest>\n" +
            "      <requestId>3ced3f70-a2b4-42d4-9e46-2fe4cac6b4bf</requestId>\n" +
            "      <resourceClassName>DEFAULT</resourceClassName>\n" +
            "      <encodedPublicKey>MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtZC7nbyxIqHdncRCXV6wBtBfXtMjuz0TQLd20Hunnr/982wFMqRfsBqEI4+Q/KnPV+N1rsKGhTrAzOCnISDFO5d111qOrWWd/X0T3AjoBLu2yFwtsc+2PYXxM7aAwPl1YfBsmvDjc+BlZEmPgIVLTbkYW2dXaOKVWi5CHpcbHuzox3stStSF9C2CT49N7URwL5qQ7f55BA4kQ1U1grnQR9nbFWT0HjiVIeZow+9ofRD6Io/T6+sMS2LWb3E+YMK6DCdStlYwmZEu+2HpqBjRqB7/3nfO74djpnUXLMzSFIv4x95ZFAeV0GTvLbflfTRd9G9Wa5CF5hd9zrj5OMNwAwIDAQAB</encodedPublicKey>\n" +
            "    </requests.RevocationRequest>\n" +
            "  </taRequests>\n" +
            "  <siaDescriptors/>\n" +
            "</requests.TrustAnchorRequest>";
}
