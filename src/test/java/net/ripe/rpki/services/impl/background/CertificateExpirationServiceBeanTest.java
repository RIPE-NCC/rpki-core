package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class CertificateExpirationServiceBeanTest {

    private ResourceCertificateRepository resourceCertificateRepository;

    private CertificateExpirationServiceBean subject;

    @Before
    public void setUp() {
        resourceCertificateRepository = mock(ResourceCertificateRepository.class);

        subject = new CertificateExpirationServiceBean(mock(ActiveNodeService.class), resourceCertificateRepository, new SimpleMeterRegistry());
    }

    @Test
    public void should_expire_outgoing_resource_certificates() {
        when(resourceCertificateRepository.expireOutgoingResourceCertificates(isA(DateTime.class)))
            .thenReturn(new ResourceCertificateRepository.ExpireOutgoingResourceCertificatesResult(0, 0, 0));

        subject.runService();

        verify(resourceCertificateRepository).expireOutgoingResourceCertificates(isA(DateTime.class));
    }
}
