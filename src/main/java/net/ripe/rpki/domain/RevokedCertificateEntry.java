package net.ripe.rpki.domain;

import org.joda.time.DateTime;
import java.math.BigInteger;

public record RevokedCertificateEntry(BigInteger serial, DateTime revocationTime) {
}
