package net.ripe.rpki.util;

import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.Entity;

import java.math.BigInteger;

public interface DBComponent {
    void lock(Entity entity);

    void lockAndRefresh(Entity entity);

    BigInteger nextSerial(HostedCertificateAuthority caId);
}
