package net.ripe.rpki.util;

import org.junit.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeTest {

    @Test
    public void formatDuration() {
        assertThat(Time.formatDuration(Duration.ZERO)).isEqualTo("0 seconds");
        assertThat(Time.formatDuration(Duration.ofMillis(503))).isEqualTo("0 seconds");
        assertThat(Time.formatDuration(Duration.ofDays(7))).isEqualTo("7 days");
        assertThat(Time.formatDuration(Duration.ofDays(1).plusHours(8).plusMinutes(23).plusSeconds(4)))
            .isEqualTo("1 day 8 hours 23 minutes 4 seconds");
    }
}
