package net.ripe.rpki.bgpris.riswhois;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
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

    public Pair<String, Long> fetch(String url) throws IOException {
        var content = getContent(url);
        try (InputStream unzipped = new GZIPInputStream(content.getLeft())) {
            return Pair.of(IOUtils.toString(unzipped, StandardCharsets.UTF_8), content.getRight());
        }
    }

    protected Pair<InputStream, Long> getContent(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT);
        connection.setReadTimeout(HTTP_TIMEOUT);
        return Pair.of(connection.getInputStream(), connection.getLastModified());
    }
}
