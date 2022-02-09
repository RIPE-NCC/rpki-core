package net.ripe.rpki.ui.commons;

import org.apache.wicket.markup.html.WebResource;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.apache.wicket.util.resource.IResourceStream;

import java.io.IOException;
import java.io.OutputStream;

public abstract class WebResourceExtension extends WebResource {

    private static final long serialVersionUID = 1L;


    @Override
    public IResourceStream getResourceStream() {
        return new AbstractResourceStreamWriter() {
            private static final long serialVersionUID = 1L;

            @Override
            public void write(OutputStream output) {
                try {
                    output.write(getEncodedObject());
                } catch (IOException e) {
                    throw new UserCommunicationException("Unable to write to output stream for resource (when user downloads resource)", e);
                }
            }

            @Override
            public String getContentType() {
                return getMimeType();
            }
        };
    }

    @Override
    protected void setHeaders(WebResponse response) {
        super.setHeaders(response);
        response.setAttachmentHeader(getFileName());
    }

    protected abstract byte[] getEncodedObject() throws IOException;

    protected abstract String getMimeType();

    protected abstract String getFileName();
}
