package net.ripe.rpki.bgpris.riswhois;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTimeConstants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Component
public class RisWhoisFetcher {

    private static final int HTTP_TIMEOUT = 30 * DateTimeConstants.MILLIS_PER_SECOND;

    public String fetch(String url) throws IOException {
        try (InputStream unzipped = new GZIPInputStream(getContent(url))) {
            return IOUtils.toString(unzipped, StandardCharsets.UTF_8);
        }
    }

    protected InputStream getContent(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT);
        connection.setReadTimeout(HTTP_TIMEOUT);
        return connection.getInputStream();
    }
}
