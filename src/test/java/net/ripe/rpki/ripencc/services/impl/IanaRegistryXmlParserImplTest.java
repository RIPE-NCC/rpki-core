package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.IpResourceSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IanaRegistryXmlParserImplTest {

    private static final IpResourceSet TEST_RIPENCC_DELEGATIONS = IpResourceSet.parse(
            "AS7, AS28, AS137, AS224, AS248-AS251, AS261, AS286, AS288, AS294, AS375, " +
                    "AS378, AS513, AS517, AS528-AS529, AS544, AS553, AS559, AS565, AS590, " +
                    "AS593, AS669, AS679-AS680, AS695-AS697, AS709-AS710, AS712, AS719, " +
                    "AS760-AS761, AS764, AS766, AS774-AS783, AS786, AS789-AS790, AS1101-AS1200, " +
                    "AS1203, AS1205, AS1213, AS1234-AS1235, AS1241, AS1248, AS1253, AS1257, " +
                    "AS1267-AS1275, AS1279, AS1290, AS1297, AS1299-AS1309, AS1318, AS1342, " +
                    "AS1352-AS1353, AS1547, AS1653-AS1654, AS1663, AS1680, AS1707-AS1726, " +
                    "AS1729, AS1732, AS1738-AS1739, AS1741, AS1748, AS1752, AS1754-AS1756, " +
                    "AS1759, AS1764, AS1770-AS1771, AS1774, AS1776, AS1780, AS1833, AS1835-AS1837, " +
                    "AS1841, AS1849-AS1850, AS1853-AS1854, AS1877-AS1903, AS1921-AS1923, " +
                    "AS1926, AS1930, AS1935-AS1955, AS1960-AS1962, AS1967, AS2004, AS2012, " +
                    "AS2016-AS2017, AS2026-AS2029, AS2036, AS2038-AS2040, AS2043, AS2045, " +
                    "AS2047, AS2049, AS2057-AS2136, AS2147-AS2148, AS2174-AS2273, AS2278-AS2377, " +
                    "AS2380, AS2387-AS2488, AS2494, AS2529-AS2530, AS2541, AS2546-AS2547, " +
                    "AS2578, AS2585-AS2614, AS2643, AS2647, AS2683, AS2766, AS2773-AS2822, " +
                    "AS2830-AS2879, AS2895, AS2917, AS2921, AS3058, AS3083-AS3109, AS3151, " +
                    "AS3154-AS3207, AS3209-AS3353, AS3412-AS3415, AS3624, AS3843, AS3917-AS3918, " +
                    "AS4148, AS4405-AS4430, AS4457-AS4458, AS4524, AS4588-AS4589, AS4974, " +
                    "AS5089, AS5377-AS5535, AS5537-AS5631, AS6067, AS6085, AS6168, AS6320, " +
                    "AS6412, AS6656-AS6712, AS6714-AS6878, AS6880-AS6911, AS8093, AS8192-AS8523, " +
                    "AS8525-AS8769, AS8771-AS9128, AS9130-AS9215, AS11341, AS11660, AS12046, " +
                    "AS12288-AS12454, AS12456-AS12555, AS12557-AS13223, AS13225-AS13311, " +
                    "AS13879, AS15360-AS15398, AS15400-AS15474, AS15476-AS15705, AS15707-AS15803, " +
                    "AS15805-AS15824, AS15826-AS15833, AS15835-AS15963, AS15965-AS16057, " +
                    "AS16059-AS16213, AS16215-AS16283, AS16285-AS16383, AS18732, AS19178, " +
                    "AS19376, AS19399, AS20480-AS20483, AS20485-AS20857, AS20859-AS20927, " +
                    "AS20929-AS21002, AS21004-AS21151, AS21153-AS21241, AS21243-AS21270, " +
                    "AS21272-AS21277, AS21279, AS21281-AS21390, AS21392-AS21451, AS21453-AS21503, " +
                    "AS22108, AS22627, AS22683, AS23242, AS24576-AS24735, AS24737-AS24756, " +
                    "AS24758-AS24787, AS24789-AS24800, AS24802-AS24834, AS24836-AS24862, " +
                    "AS24864-AS24877, AS24879-AS24986, AS24988-AS25162, AS25164-AS25249, " +
                    "AS25251-AS25361, AS25363, AS25365-AS25542, AS25544-AS25567, AS25569-AS25575, " +
                    "AS25577-AS25599, AS25880, AS28672-AS28682, AS28684-AS28697, AS28699-AS28912, " +
                    "AS28914-AS29090, AS29092-AS29337, AS29339, AS29341-AS29427, AS29429-AS29494, " +
                    "AS29496-AS29543, AS29545-AS29570, AS29572-AS29613, AS29615-AS29673, " +
                    "AS29675-AS29695, AS30720-AS30895, AS30897-AS30979, AS30981, AS31000-AS31064, " +
                    "AS31066-AS31244, AS31246-AS31618, AS31620-AS31743, AS33792-AS35839, " +
                    "AS38912-AS39935, AS40960-AS45055, AS47104-AS52223, AS56320-AS58367, " +
                    "AS59392-AS61439, AS196608-AS199679, " +
                    "2.0.0.0/8, 5.0.0.0/8, 25.0.0.0/8, 31.0.0.0/8, 37.0.0.0/8, 46.0.0.0/8, " +
                    "51.0.0.0/8, 62.0.0.0/8, 77.0.0.0-95.255.255.255, 109.0.0.0/8, 141.0.0.0/8, " +
                    "145.0.0.0/8, 151.0.0.0/8, 176.0.0.0/8, 178.0.0.0/8, 185.0.0.0/8, 188.0.0.0/8, " +
                    "193.0.0.0-195.255.255.255, 212.0.0.0/7, 217.0.0.0/8, 2001:600::-2001:bff:ffff:ffff:ffff:ffff:ffff:ffff, " +
                    "2001:1400::/22, 2001:1a00::-2001:3bff:ffff:ffff:ffff:ffff:ffff:ffff, " +
                    "2001:4000::/23, 2001:4600::/23, 2001:4a00::-2001:4dff:ffff:ffff:ffff:ffff:ffff:ffff, " +
                    "2001:5000::/20, 2003::/18, 2a00::/12");

    private static final String IANA_ASN_DELEGATIONS = "src/test/resources/iana/as-numbers.xml";
    private static final String IANA_IPv4_DELEGATIONS = "src/test/resources/iana/ipv4-address-space.xml";
    private static final String IANA_IPv6_DELEGATIONS = "src/test/resources/iana/ipv6-unicast-address-assignments.xml";


    @Test
    public void should_read_delegation_files_from_IANA() {
        IanaRegistryXmlParserImpl subject = new IanaRegistryXmlParserImpl(IANA_ASN_DELEGATIONS, IANA_IPv4_DELEGATIONS, IANA_IPv6_DELEGATIONS);

        assertEquals(TEST_RIPENCC_DELEGATIONS, subject.getRirResources(IanaRegistryXmlParserImpl.MajorityRir.RIPE));
    }

    @Test
    public void should_fail_if_IPv4_delegations_file_cannot_be_found() {

        IanaRegistryXmlParserImpl subject = new IanaRegistryXmlParserImpl(IANA_ASN_DELEGATIONS, "missing/iana.ipv4.xml", IANA_IPv6_DELEGATIONS);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> subject.getRirResources(IanaRegistryXmlParserImpl.MajorityRir.RIPE));
        assertEquals("File 'missing/iana.ipv4.xml' does not exist or cannot be read by the application", illegalArgumentException.getMessage());
    }

    @Test
    public void should_fail_if_IPv6_delegations_file_cannot_be_found() {
        IanaRegistryXmlParserImpl subject = new IanaRegistryXmlParserImpl(IANA_ASN_DELEGATIONS, IANA_IPv4_DELEGATIONS, "missing/iana.ipv6.xml");

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> subject.getRirResources(IanaRegistryXmlParserImpl.MajorityRir.RIPE));
        assertEquals("File 'missing/iana.ipv6.xml' does not exist or cannot be read by the application",illegalArgumentException.getMessage());
    }

}
