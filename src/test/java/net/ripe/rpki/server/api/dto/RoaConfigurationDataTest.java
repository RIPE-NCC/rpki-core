package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RoaConfigurationDataTest {

    @Test
    public void should_calculate_entity_tag() {
        RoaConfigurationData data0 = new RoaConfigurationData(Collections.emptyList());
        RoaConfigurationData data1 = new RoaConfigurationData(Arrays.asList(
            new RoaConfigurationPrefixData(Asn.parse("AS3333"), IpRange.parse("192.168.0.0/16"), 16),
            new RoaConfigurationPrefixData(Asn.parse("AS3333"), IpRange.parse("10.0.0.0/8"), 16)
        ));
        List<RoaConfigurationPrefixData> reversed = new ArrayList<>(data1.getPrefixes());
        Collections.reverse(reversed);
        RoaConfigurationData data2 = new RoaConfigurationData(reversed);

        assertThat(data0.entityTag()).isEqualTo("\"47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=\"");
        assertThat(data1.entityTag()).isEqualTo("\"nheoXmNx+i7Dy/djurhv+XAyVBzIwg6e1cwHyWWigjs=\"");
        assertThat(data2.entityTag()).isEqualTo(data1.entityTag());
    }
}
