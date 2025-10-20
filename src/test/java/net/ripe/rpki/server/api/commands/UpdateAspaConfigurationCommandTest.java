package net.ripe.rpki.server.api.commands;


import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.rest.service.Aspas;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdateAspaConfigurationCommandTest {

    @Test
    public void aspaConfigurationIETF_Empty() {
        var aspa = new AspaConfigurationData(Asn.parse("AS13"), List.of());
        assertThat(AspaConfigurationData.inIETFNotation(aspa)).isEqualTo("AS13 => []");
    }

    @Test
    public void aspaConfigurationIETF() {
        var aspa = new AspaConfigurationData(Asn.parse("AS13"), List.of(Asn.parse("AS4321"), Asn.parse("AS1234")));
        assertThat(AspaConfigurationData.inIETFNotation(aspa)).isEqualTo("AS13 => [AS1234, AS4321]");
    }

    @Test
    public void getCommandSummary_BothEmpty() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(
                new VersionedId(1), "etag",
                Collections.emptyList(),
                Collections.emptyMap()
        );
        assertThat(command.getCommandSummary()).isEqualTo("Updated ASPA configuration: no changes.");
    }

    @Test
    public void getCommandSummary_NonEmpty() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(
                new VersionedId(1), "etag",
                Collections.emptyList(),
                Map.of(
                        new Asn(10),
                        new Aspas.AspaDiff(
                                Set.of(new Asn(20), new Asn(30)),
                                Set.of(new Asn(20), new Asn(40))),
                        new Asn(50),
                        new Aspas.AspaDiff(
                                Set.of(new Asn(60), new Asn(70)),
                                Set.of()),
                        new Asn(100),
                        new Aspas.AspaDiff(
                                Set.of(),
                                Set.of(new Asn(200), new Asn(300)))
                )
        );
        assertThat(command.getCommandSummary()).isEqualTo(
                "Updated ASPA configuration: " +
                        "AS10 => [AS20, AS30] (was: [AS20, AS40]); " +
                        "AS50 => [AS60, AS70] (was: []); " +
                        "AS100 => [] (was: [AS200, AS300]).");
    }

}
