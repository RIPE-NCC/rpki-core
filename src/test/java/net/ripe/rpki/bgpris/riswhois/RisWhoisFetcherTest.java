package net.ripe.rpki.bgpris.riswhois;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


@WireMockTest
class RisWhoisFetcherTest {
    byte[] risDumpContent;

    RisWhoisFetcher subject;

    @BeforeEach
    void setup() throws Exception {
        subject = new RisWhoisFetcher();

        risDumpContent = RisWhoisFetcherTest.class.getResourceAsStream("/static/riswhois/riswhoisdump-head-1000.IPv4.gz").readAllBytes();
    }
    @Test
    void testFetch(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        var path = "/" + RandomStringUtils.randomAlphanumeric(16) + ".gz";

        stubFor(
                get(urlEqualTo(path))
                .willReturn(aResponse().withBody(risDumpContent))
        );

        String data = subject.fetch(wmRuntimeInfo.getHttpBaseUrl() + path);
        assertThat(data).contains("45528\t1.22.52.0/23\t99");
    }
}
