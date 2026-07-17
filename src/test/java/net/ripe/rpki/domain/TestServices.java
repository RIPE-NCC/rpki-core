package net.ripe.rpki.domain;

import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;

import static org.mockito.Mockito.mock;

public class TestServices {
    public static CertificateFactory createSingleUseEeCertificateFactory() {
        return new CertificateFactory(new InMemoryResourceCertificateRepository(), mock(BgpSecCertificateRepository.class), null);
    }
}
