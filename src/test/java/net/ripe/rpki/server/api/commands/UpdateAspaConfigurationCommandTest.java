package net.ripe.rpki.server.api.commands;


import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdateAspaConfigurationCommandTest {

    @Test
    public void getCommandSummary() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(
            new VersionedId(1),
            "etag",
            Collections.singletonList(new AspaConfigurationData(
                Asn.parse("AS13"),
                List.of(Asn.parse("AS1234"))
            ))
        );

        assertThat(command.getCommandSummary()).isEqualTo("Update ASPA configuration to: AS13 -> AS1234.");
    }
}