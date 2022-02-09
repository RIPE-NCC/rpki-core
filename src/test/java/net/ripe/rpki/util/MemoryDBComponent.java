package net.ripe.rpki.util;

import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.Entity;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class MemoryDBComponent implements DBComponent {

    private BigInteger serial = BigInteger.ZERO;
    private ReentrantLock lock = new ReentrantLock();

    @Override
    public void lock(Entity entity) {
        // do nothing for now
    }

    @Override
    public void lockAndRefresh(Entity entity) {
        // do nothing for now
    }

    @Override
    public synchronized BigInteger nextSerial(HostedCertificateAuthority ca) {
        serial = ca.getLastIssuedSerial().add(BigInteger.ONE);
        ca.setLastIssuedSerial(serial);
        return this.serial;
    }

}
