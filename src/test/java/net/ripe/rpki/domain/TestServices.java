package net.ripe.rpki.domain;

import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;

public class TestServices {

    public static SingleUseEeCertificateFactory createSingleUseEeCertificateFactory() {
        return new SingleUseEeCertificateFactory(new InMemoryResourceCertificateRepository());
    }

}
