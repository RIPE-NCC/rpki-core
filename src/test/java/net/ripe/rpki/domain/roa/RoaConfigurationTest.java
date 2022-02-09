package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RoaConfigurationTest {

    private static final RoaConfigurationPrefix AS3333_10_8_NULL = new RoaConfigurationPrefix(Asn.parse("AS3333"), IpRange.parse("10/8"), null);
    private static final RoaConfigurationPrefix AS3333_10_8_16 = new RoaConfigurationPrefix(Asn.parse("AS3333"), IpRange.parse("10/8"), 16);

    private HostedCertificateAuthority certificateAuthority;
    private RoaConfiguration subject;

    @Before
    public void setUp() {
        certificateAuthority = mock(HostedCertificateAuthority.class);
        subject = new RoaConfiguration(certificateAuthority, Collections.emptyList());
    }

    @Test
    public void should_add_roa_prefix() {
        subject.addPrefix(Collections.singleton(AS3333_10_8_NULL));

        assertEquals(Collections.singleton(AS3333_10_8_NULL), subject.getPrefixes());
    }

    @Test
    public void should_replace_roa_prefix_when_only_maximum_length_changed() {
        subject.addPrefix(Collections.singleton(AS3333_10_8_NULL));
        subject.addPrefix(Collections.singleton(AS3333_10_8_16));

        assertEquals(Collections.singleton(AS3333_10_8_16), subject.getPrefixes());
    }

    @Test
    public void should_remove_roa_prefix() {
        subject.addPrefix(Collections.singleton(AS3333_10_8_NULL));

        subject.removePrefix(Collections.singleton(AS3333_10_8_NULL));

        assertEquals(Collections.EMPTY_SET, subject.getPrefixes());
    }
}
