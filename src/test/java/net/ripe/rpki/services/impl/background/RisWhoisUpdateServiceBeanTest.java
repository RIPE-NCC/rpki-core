package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.bgpris.BgpRisEntryRepositoryBean;
import net.ripe.rpki.bgpris.riswhois.RisWhoisFetcher;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RisWhoisUpdateServiceBeanTest {

    private static final String BASE_URL = "http://some.where.over.the.rainbow/";
    private static final String IPV4_FILE_URL = BASE_URL + "/" + RisWhoisUpdateServiceBean.FILENAMES[0];
    private static final String IPV6_FILE_URL = BASE_URL + "/" + RisWhoisUpdateServiceBean.FILENAMES[1];

    @Mock
    ActiveNodeService propertyService;

    @Mock
    BgpRisEntryRepositoryBean repository;

    @Mock
    RisWhoisFetcher fetcher;

    private RisWhoisUpdateServiceBean subject;

    @Before
    public void setUp() {
        subject = new RisWhoisUpdateServiceBean(propertyService, repository, BASE_URL, fetcher, new SimpleMeterRegistry());
    }


    @SuppressWarnings({"unchecked"})
    @Test
    public void shouldUpdateRepositoryWhenMoreThan100kEntriesFound() throws IOException {
        when(fetcher.fetch(IPV4_FILE_URL)).thenReturn(getTestLines(100001));
        when(fetcher.fetch(IPV6_FILE_URL)).thenReturn(getTestLines(0));

        subject.runService();

        verify(repository).resetEntries(isA(List.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void shouldNotFailOnPartiallyBrokenFile() throws IOException {
        when(fetcher.fetch(IPV4_FILE_URL)).thenReturn(getTestLines(100001));
        when(fetcher.fetch(IPV6_FILE_URL)).thenReturn(
                "207841\t::ffff:0.0.0.0/96\t1\n" +
                "268624\t::ffff:45.164.124.0/120\t1\n" +
                "268624\t::ffff:45.164.125.0/120\t1\n" +
                "268624\t::ffff:45.164.126.0/120\t1\n" +
                "268624\t::ffff:45.164.127.0/120\t1\n" +
                "268624\t::ffff:80.94.90.0/120\t1\n"
        );

        subject.runService();

        verify(repository).resetEntries(isA(List.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void shouldNOTUpdateRepositoryWhenLessThan100kEntriesFound() throws IOException {
        when(fetcher.fetch(IPV4_FILE_URL)).thenReturn(getTestLines(0));
        when(fetcher.fetch(IPV6_FILE_URL)).thenReturn(getTestLines(99999));

        subject.runService();

        verify(repository, never()).resetEntries(isA(Collection.class));
    }

    @Test
    public void shouldHandleExceptionsGracefully() throws IOException {
        when(fetcher.fetch(IPV4_FILE_URL)).thenThrow(new IOException());
        when(fetcher.fetch(IPV6_FILE_URL)).thenThrow(new IOException());

        // No uncaught exception
        subject.runService();
    }

    private String getTestLines(int lines) {
        StringBuilder responseBuilder = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            responseBuilder.append(i + 1).append("\t10.0.0.0/8\t10\n");
        }
        return responseBuilder.toString();
    }
}
