package net.ripe.rpki.ripencc.support.persistence;

import org.hibernate.type.descriptor.java.BigIntegerJavaType;

import java.math.BigInteger;

/**
 * Hibernate's {@link BigIntegerJavaType} hashes a value using only its lowest 32 bits ({@link BigInteger#intValue()}).
 * For IPv6 prefix bounds these bits are all zeros (start) or all ones (end) for any prefix of length /96 or shorter,
 * so entity keys containing them all collide in the persistence context, making loading quadratic in the number of
 * entities. Use the full value hash code instead.
 */
public class FullHashBigIntegerJavaType extends BigIntegerJavaType {
    @Override
    public int extractHashCode(BigInteger value) {
        return value.hashCode();
    }
}
