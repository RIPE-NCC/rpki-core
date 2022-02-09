package net.ripe.rpki.bgpris.riswhois;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class RisWhoisParserTest {

    @Test
    public void shouldParseEmptyFile() {
        assertEquals(new ArrayList<BgpRisEntry>(), RisWhoisParser.parse(""));
    }

    @Test
    public void shouldParseSingleIpv4Entry() {
        assertEquals(Collections.singletonList(new BgpRisEntry(Asn.parse("3333"), IpRange.parse("127.0.0.0/8"), 201)), RisWhoisParser.parse("3333\t127.0.0.0/8\t201"));
    }

    @Test
    public void shouldParseSingleIpv6Entry() {
        assertEquals(Collections.singletonList(new BgpRisEntry(Asn.parse("24490"), IpRange.parse("2001:254:8000::/33"), 62)), RisWhoisParser.parse("24490\t2001:254:8000::/33\t62\n"));
    }

    @Test
    public void shouldParseIpv4MappedIpv6Entry() {
        assertEquals(Collections.singletonList(new BgpRisEntry(Asn.parse("34231"), IpRange.parse("::216.66.38.58/128"), 62)), RisWhoisParser.parse("34231\t::216.66.38.58/128\t62\n"));
    }

    @Test
    public void shouldSkipMalformedEntry() {
        assertEquals(Collections.singletonList(new BgpRisEntry(Asn.parse("24490"), IpRange.parse("2001:254:8000::/33"), 62)), RisWhoisParser.parse("24490\t2001:254:8000::/33\t62\n34231\t1:2::::::\t62\n"));
    }

    @Test
    public void shouldParseMultipleEntries() {
        assertEquals(
                Arrays.asList(
                        new BgpRisEntry(Asn.parse("3333"), IpRange.parse("127.0.0.0/8"), 201),
                        new BgpRisEntry(Asn.parse("4545"), IpRange.parse("192.168.0.0/16"), 332)),
                RisWhoisParser.parse("3333\t127.0.0.0/8\t201\n4545\t192.168.0.0/16\t332\n"));
    }

    @Test
    public void shouldSkipUnmatchedLines() {
        assertEquals(Collections.singletonList(new BgpRisEntry(Asn.parse("3333"), IpRange.parse("127.0.0.0/8"), 201)), RisWhoisParser.parse("%This is a comment\n3333\t127.0.0.0/8\t201"));
    }

}
