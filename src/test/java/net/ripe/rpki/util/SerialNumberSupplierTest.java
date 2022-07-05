package net.ripe.rpki.util;

import org.joda.time.DateTimeUtils;
import org.junit.Test;

import java.math.BigInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SerialNumberSupplierTest {

    private SerialNumberSupplier subject = SerialNumberSupplier.getInstance();

    @Test
    public void should_generate_random_serial_between_minimum_and_maximum() {
        BigInteger minimum = BigInteger.ZERO;
        BigInteger maximum = BigInteger.ONE.shiftLeft(159).subtract(BigInteger.ONE);

        for (int i = 0; i < 10000; ++i) {
            BigInteger serialNumber = subject.get();
            assertThat(serialNumber).isBetween(minimum, maximum);
        }
    }

    @Test
    public void should_generate_unique_serial_numbers() {
        long size = 1000;
        long distinctCount = Stream.generate(() -> subject.get()).limit(size).distinct().count();
        assertThat(distinctCount).isEqualTo(size);
    }

    @Test
    public void should_start_serial_with_current_time() {
        try {
            DateTimeUtils.setCurrentMillisFixed(0x1234567890abcdefL);
            BigInteger serialNumber = subject.get();
            byte[] bytes = serialNumber.toByteArray();
            assertThat(bytes).hasSize(20).startsWith(0x12, 0x34, 0x56, 0x78, 0x90, 0xab, 0xcd, 0xef);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

}
