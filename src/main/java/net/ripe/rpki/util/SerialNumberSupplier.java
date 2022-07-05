package net.ripe.rpki.util;

import org.joda.time.DateTimeUtils;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.function.Supplier;

public final class SerialNumberSupplier implements Supplier<BigInteger> {

    public static final int SERIAL_RANDOM_BITS = 8 * 12;
    private static final SerialNumberSupplier INSTANCE = new SerialNumberSupplier();

    private SecureRandom secureRandom = new SecureRandom();

    public static SerialNumberSupplier getInstance() {
        return INSTANCE;
    }

    @Override
    public BigInteger get() {
        BigInteger now = BigInteger.valueOf(DateTimeUtils.currentTimeMillis());
        BigInteger random = new BigInteger(SERIAL_RANDOM_BITS, secureRandom);
        return now.shiftLeft(SERIAL_RANDOM_BITS).or(random);
    }

}
