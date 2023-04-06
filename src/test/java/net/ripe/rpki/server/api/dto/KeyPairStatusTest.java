package net.ripe.rpki.server.api.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static net.ripe.rpki.server.api.dto.KeyPairStatus.*;


public class KeyPairStatusTest {
    /**
     * Ensure that enum ordering is not changed - because we depend on this.
     */
    @Test
    void keyPairStatusFollowsLifecycleOrder() {
        for (var i=0; i< 7; i++) {
            // Check that a random shuffle is correctly re-sorted
            var statuses = new ArrayList<>(List.of(KeyPairStatus.values()));

            Collections.shuffle(statuses);
            Collections.sort(statuses);

            assertThat(statuses)
                    .containsExactly(PENDING, CURRENT, OLD);
        }
    }
}
