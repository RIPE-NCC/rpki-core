package net.ripe.rpki.bgpris;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.*;

public class BgpRisEntryRepositoryBeanTest {

    private static final BgpRisEntry BGP_RIS_ENTRY_IPV6_LARGE = new BgpRisEntry(Asn.parse("65000"), IpRange.parse("ff00::/11"), 20);
    private static final BgpRisEntry BGP_RIS_ENTRY_IPV4_LARGE = new BgpRisEntry(Asn.parse("65000"), IpRange.parse("6.0.0.0/7"), 20);
    private static final BgpRisEntry BGP_RIS_ENTRY_BELOW_TRESHOLD = new BgpRisEntry(Asn.parse("65000"), IpRange.parse("10.0.0.0/8"), 1);
    private static final BgpRisEntry BGP_RIS_ENTRY_193_8 = new BgpRisEntry(Asn.parse("3333"), IpRange.parse("193.0.0.0/8"), 5);
    private static final BgpRisEntry BGP_RIS_ENTRY_FFCE_16 = new BgpRisEntry(Asn.parse("3333"), IpRange.parse("ffce::/16"), 6);
    private static final BgpRisEntry BGP_RIS_ENTRY_193_16 = new BgpRisEntry(Asn.parse("65535"), IpRange.parse("193.16.0.0/16"), 5);

    private BgpRisEntryRepositoryBean subject;

    @Before
    public void setup() {
        subject = new BgpRisEntryRepositoryBean();
        subject.resetEntries(Arrays.asList(BGP_RIS_ENTRY_BELOW_TRESHOLD, BGP_RIS_ENTRY_193_8, BGP_RIS_ENTRY_FFCE_16, BGP_RIS_ENTRY_IPV6_LARGE, BGP_RIS_ENTRY_IPV4_LARGE));
    }

    @Test
    public void should_find_entries_with_overlapping_prefixes() {
        assertEquals(entries(BGP_RIS_ENTRY_193_8), subject.findMostSpecificOverlapping(IpResourceSet.parse("0.0.0.0/0")));
        assertEquals(entries(BGP_RIS_ENTRY_193_8), subject.findMostSpecificOverlapping(IpResourceSet.parse("192.0.0.0-193.0.0.1")));
        assertEquals(entries(BGP_RIS_ENTRY_FFCE_16), subject.findMostSpecificOverlapping(IpResourceSet.parse("ffce:abcd::/32")));
        assertEquals(entries(BGP_RIS_ENTRY_193_8, BGP_RIS_ENTRY_FFCE_16), subject.findMostSpecificOverlapping(IpResourceSet.parse("0.0.0.0/0, ffce:abcd::/32")));
    }

    @Test
    public void should_skip_entries_below_threshold() {
        assertEquals(entries(), subject.findMostSpecificOverlapping(IpResourceSet.parse("10.0.0.0/8")));
    }

    @Test
    public void should_skip_large_prefixes() {
        assertEquals(entries(), subject.findMostSpecificOverlapping(IpResourceSet.parse("6.0.0.0/8")));
        assertEquals(entries(), subject.findMostSpecificOverlapping(IpResourceSet.parse("ff00::/12")));
    }

    @Test
    public void should_skip_entries_without_overlapping_prefixes() {
        assertEquals(entries(), subject.findMostSpecificOverlapping(IpResourceSet.parse("9.0.0.0/8")));
    }

    @Test
    public void should_not_match_entries_by_origin_asn() {
        assertEquals(entries(), subject.findMostSpecificOverlapping(IpResourceSet.parse("AS3333")));
    }

    @Test
    public void should_only_match_most_specific_routing_entries_when_resources_are_full_covered() {
        subject.resetEntries(entries(BGP_RIS_ENTRY_193_16, BGP_RIS_ENTRY_193_8));
        assertEquals(entries(BGP_RIS_ENTRY_193_16), subject.findMostSpecificOverlapping(IpResourceSet.parse("193.16.0.0/17")));
    }

    @Test
    public void should_match_all_routing_entries_when_most_specific_do_not_fully_cover_resources() {
        subject.resetEntries(entries(BGP_RIS_ENTRY_193_16, BGP_RIS_ENTRY_193_8));
        assertEquals(entries(BGP_RIS_ENTRY_193_8, BGP_RIS_ENTRY_193_16), subject.findMostSpecificOverlapping(IpResourceSet.parse("193.16.0.0/15")));
    }

    @Test
    public void should_split_in_contained_and_not_contained() {
        subject.resetEntries(entries(BGP_RIS_ENTRY_193_16, BGP_RIS_ENTRY_193_8));
        Map<Boolean, Collection<BgpRisEntry>> result = subject.findMostSpecificContainedAndNotContained(IpResourceSet.parse("193.16.0.0/15"));
        assertEquals(entries(BGP_RIS_ENTRY_193_16), result.get(true));
        assertEquals(entries(BGP_RIS_ENTRY_193_8), result.get(false));

    }

    @Test
    public void should_find_less_specific_only_if_resources_remain_after_exact_and_more_specific() {
        /*
         * Bug reported:
         *
         * For range: 91.194.96.0-91.194.101.255
         *
         * The following exact and more specific announcements were seen in BGP:
         * 91.194.96.0/22 (announced by AS43142)
         * 91.194.98.0/24 (announced by AS43142)
         * 91.194.100.0/24 (announced by AS43142)
         * 91.194.101.0/24 (announced by AS43142)
         *
         * And this less specific:
         * 91.0.0.0/8 (announced by AS9155)
         *
         * However the exact and more specific announcements cover the full range,
         * so the 91/8 should not show up as less specific in the BGP previews.
         */
        BgpRisEntry BGP_96_22 = new BgpRisEntry(Asn.parse("AS43142"), IpRange.parse("91.194.96.0/22"), 20);
        BgpRisEntry BGP_98_24 = new BgpRisEntry(Asn.parse("AS43142"), IpRange.parse("91.194.98.0/24"), 20);
        BgpRisEntry BGP_100_24 = new BgpRisEntry(Asn.parse("AS43142"), IpRange.parse("91.194.100.0/24"), 20);
        BgpRisEntry BGP_101_24 = new BgpRisEntry(Asn.parse("AS43142"), IpRange.parse("91.194.101.0/24"), 20);
        BgpRisEntry BGP_91_8 = new BgpRisEntry(Asn.parse("AS9155"), IpRange.parse("91.0.0.0/8"), 20);

        BgpRisEntryRepositoryBean bgpRisEntryRepositoryBean = new BgpRisEntryRepositoryBean();
        bgpRisEntryRepositoryBean.resetEntries(Arrays.asList(BGP_96_22, BGP_98_24, BGP_100_24, BGP_101_24, BGP_91_8));

        assertEquals(entries(BGP_96_22, BGP_98_24, BGP_100_24, BGP_101_24), bgpRisEntryRepositoryBean.findMostSpecificOverlapping(IpResourceSet.parse("91.194.96.0-91.194.101.255")));
    }

    @Test
    public void should_suppport_multiple_asns_for_the_same_prefix() {
        /*
         * Bug reported:
         *
         * The user has for pairs (ASN, Prefix)
         *
         * 207021  176.97.158.0/24         207
         * 1921    176.97.158.0/24         83
         * 207021  2001:67c:10b8::/48      232
         * 1921    2001:67c:10b8::/48      46
         *
         * but is able to see only two in the RPKI Dashboard
         *
         * 1921    176.97.158.0/24         83
         * 1921    2001:67c:10b8::/48      46
         *
         * Should be able to see all four.
         *
         */
        BgpRisEntry BGP_IPV4_1 = new BgpRisEntry(Asn.parse("AS207021"), IpRange.parse("176.97.158.0/24"), 83);
        BgpRisEntry BGP_IPV4_2 = new BgpRisEntry(Asn.parse("AS1921"), IpRange.parse("176.97.158.0/24"), 207);
        BgpRisEntry BGP_IPV6_1 = new BgpRisEntry(Asn.parse("AS207021"), IpRange.parse("2001:67c:10b8::/48"), 232);
        BgpRisEntry BGP_IPV6_2 = new BgpRisEntry(Asn.parse("AS1921"), IpRange.parse("2001:67c:10b8::/48"), 46);

        BgpRisEntryRepositoryBean bgpRisEntryRepositoryBean = new BgpRisEntryRepositoryBean();
        bgpRisEntryRepositoryBean.resetEntries(Arrays.asList(BGP_IPV4_1, BGP_IPV4_2, BGP_IPV6_1, BGP_IPV6_2));

        final Map<Boolean, Collection<BgpRisEntry>> announcements = bgpRisEntryRepositoryBean
            .findMostSpecificContainedAndNotContained(IpResourceSet.parse("176.97.158.0/24, 2001:67c:10b8::/48"));

        assertEquals(4, announcements.get(true).size());
        assertEquals(0, announcements.get(false).size());
    }

    private static Collection<BgpRisEntry> entries(BgpRisEntry... entries) {
        return new HashSet<>(Arrays.asList(entries));
    }
}
