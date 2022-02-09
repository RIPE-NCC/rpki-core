package net.ripe.rpki.ripencc.services.impl;


import java.net.URI;

class LinkUtil {
    static String   linkTarget(URI baseUri, String path) {
        int port = baseUri.getPort();
        final String sPort;
        if (port == -1) {
            sPort = "http".equalsIgnoreCase(baseUri.getScheme()) ? ":80" :
                    "https".equalsIgnoreCase(baseUri.getScheme()) ? ":443" : "";
        } else {
            sPort = ":" + port;
        }
        return baseUri.getScheme() + "://" + baseUri.getHost() + sPort + path;
    }
}
