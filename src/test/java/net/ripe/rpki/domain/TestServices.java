package net.ripe.rpki.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.domain.inmemory.InMemoryResourceCertificateRepository;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;

public class TestServices {

    public static CertificateManagementService createCertificateManagementService() {
        return createCertificateManagementService(new InMemoryResourceCertificateRepository(), null);
    }

    public static CertificateManagementService createCertificateManagementService(PublishedObjectRepository publishedObjectRepository) {
        return createCertificateManagementService(new InMemoryResourceCertificateRepository(), publishedObjectRepository);
    }

    public static CertificateManagementService createCertificateManagementService(ResourceCertificateRepository resourceCertificateRepository,
                                                                                  PublishedObjectRepository publishedObjectRepository) {
        return new CertificateManagementServiceImpl(resourceCertificateRepository, publishedObjectRepository, null, null, null, new SimpleMeterRegistry());
    }

}
