package net.ripe.rpki.ui.admin;

import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorRequestSerializer;
import net.ripe.rpki.ui.commons.WebResourceExtension;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;

public class TrustAnchorRequestResource extends WebResourceExtension {

    private static final String UTF_8 = "UTF-8";

    private static final long serialVersionUID = 1L;

    private final TrustAnchorRequest request;

    public TrustAnchorRequestResource(TrustAnchorRequest request) {
        this.request = request;
    }

    @Override
    protected byte[] getEncodedObject() throws IOException {
        TrustAnchorRequestSerializer serializer = new TrustAnchorRequestSerializer();
        return serializer.serialize(request).getBytes(UTF_8);
    }

    @Override
    protected String getFileName() {
        String createdAt = new DateTime(request.getCreationTimestamp(), DateTimeZone.UTC).toString("yyyyMMdd-HHmmss");
        return "request-" + createdAt + ".xml";
    }

    @Override
    protected String getMimeType() {
        return "application/xml";
    }
}
