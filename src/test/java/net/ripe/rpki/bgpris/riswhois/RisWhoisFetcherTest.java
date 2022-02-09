package net.ripe.rpki.bgpris.riswhois;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertTrue;


public class RisWhoisFetcherTest {

    private RisWhoisFetcher subject;

    private Server server;

    @Before
    public void setupJetty() throws Exception {
        subject = new RisWhoisFetcher();
        server = new Server(39443);
        Handler handler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.setContentType("application/x-gzip");
                response.setStatus(HttpServletResponse.SC_OK);
                IOUtils.copy(RisWhoisFetcherTest.class.getResourceAsStream("/bgpris/riswhois/riswhoisdump-head-1000.IPv4.gz"), response.getOutputStream());
                ((Request) request).setHandled(true);
            }
        };
        server.setHandler(handler);
        server.start();
    }

    @After
    public void cleanup() throws Exception {
        server.stop();
    }

    @Test
    public void test() throws Exception {
        String data = subject.fetch("http://localhost:39443/");
        assertTrue(data, data.contains("45528\t1.22.52.0/23\t99"));
    }

}
