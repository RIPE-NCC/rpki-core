package net.ripe.rpki.server.api.commands;


import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.AspaProviderData;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdateAspaConfigurationCommandTest {

    @Test
    public void getCommandSummary() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(
            new VersionedId(1),
            "etag",
            Collections.singletonList(new AspaConfigurationData(
                Asn.parse("AS13"),
                Collections.singletonList(new AspaProviderData(Asn.parse("AS1234"), AspaAfiLimit.ANY))
            ))
        );

        assertThat(command.getCommandSummary()).isEqualTo("Update ASPA configuration to: AS13 -> AS1234 [ANY].");
    }
}