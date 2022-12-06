package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PublishedObjectCleanUpServiceBeanTest {

    private PublishedObjectCleanUpServiceBean service;

    @Rule
    public FixedDateRule fixedDateRule = new FixedDateRule(new DateTime(2009, 2, 5, 0, 0, 0, 0, DateTimeZone.UTC).getMillis());

    @Mock
    private ActiveNodeService activeNodeService;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private PublishedObjectRepository publishedObjectRepository;

    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Before
    public void setUp() {
        MeterRegistry registry = new SimpleMeterRegistry();
        service = new PublishedObjectCleanUpServiceBean(new BackgroundTaskRunner(activeNodeService, registry), certificateAuthorityRepository,
            publishedObjectRepository, resourceCertificateRepository, transactionManager, registry);
        service.setDaysBeforeCleanUp(7);
    }

    @Test
    public void should_delete_expired_certificates_and_published_objects() {
        DateTime expirationTime = new DateTime(DateTimeZone.UTC).minusDays(7);

        service.runService(Collections.emptyMap());

        verify(publishedObjectRepository).deleteExpiredObjects(expirationTime);
        verify(resourceCertificateRepository).deleteExpiredOutgoingResourceCertificates(expirationTime);
        verify(certificateAuthorityRepository).deleteNonHostedPublicKeysWithoutSigningCertificates();
    }

}
