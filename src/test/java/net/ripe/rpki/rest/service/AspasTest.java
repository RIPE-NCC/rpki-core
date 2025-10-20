package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AspasTest {

    @Test
    void diffPerCustomer_UpdatedOnly() {
        var as100 = Asn.parse("AS100");
        var as200 = Asn.parse("AS200");
        var as300 = Asn.parse("AS300");

        var current = List.of(new AspaConfigurationData(as100, List.of(as200)));
        var updated = List.of(new AspaConfigurationData(as100, List.of(as300)));

        var diff = Aspas.diffPerCustomer(current, updated);

        assertThat(diff.get(as100).added()).isEqualTo(Set.of(as300));
        assertThat(diff.get(as100).deleted()).isEqualTo(Set.of(as200));
    }

    @Test
    void diffPerCustomer_UnchangedConfiguration() {
        var as100 = Asn.parse("AS100");
        var as200 = Asn.parse("AS200");
        var as300 = Asn.parse("AS300");

        var current = List.of(new AspaConfigurationData(as100, List.of(as200, as300)));
        var updated = List.of(new AspaConfigurationData(as100, List.of(as200, as300)));

        var diff = Aspas.diffPerCustomer(current, updated);
        assertThat(diff).isEmpty();
    }

    @Test
    void diffPerCustomer_PartiallyChanged() {
        var as100 = Asn.parse("AS100");
        var as200 = Asn.parse("AS200");
        var as300 = Asn.parse("AS300");
        var as400 = Asn.parse("AS400");
        var as500 = Asn.parse("AS500");

        var current = List.of(new AspaConfigurationData(as100, List.of(as200, as300)));
        var updated = List.of(
                new AspaConfigurationData(as100, List.of(as200, as300)),
                new AspaConfigurationData(as200, List.of(as400, as500))
        );

        var diff = Aspas.diffPerCustomer(current, updated);
        assertThat(diff).hasSize(1);
        assertThat(diff.get(as200).added()).isEqualTo(Set.of(as400, as500));
        assertThat(diff.get(as200).deleted()).isEmpty();
    }

    @Test
    void diffPerCustomer_AddedAndChanged() {
        var as100 = Asn.parse("AS100");
        var as200 = Asn.parse("AS200");
        var as300 = Asn.parse("AS300");
        var as400 = Asn.parse("AS400");
        var as500 = Asn.parse("AS500");

        var current = List.of(new AspaConfigurationData(as100, List.of(as200, as300)));
        var updated = List.of(
                new AspaConfigurationData(as100, List.of(as300, as500)),
                new AspaConfigurationData(as200, List.of(as400, as500))
        );

        var diff = Aspas.diffPerCustomer(current, updated);
        assertThat(diff).hasSize(2);
        assertThat(diff.get(as100).deleted()).isEqualTo(Set.of(as200, as300));
        assertThat(diff.get(as100).added()).isEqualTo(Set.of(as300, as500));
        assertThat(diff.get(as200).added()).isEqualTo(Set.of(as400, as500));
    }

}
