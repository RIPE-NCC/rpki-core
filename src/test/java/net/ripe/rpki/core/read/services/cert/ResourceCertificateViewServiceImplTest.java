package net.ripe.rpki.core.read.services.cert;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.junit.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class ResourceCertificateViewServiceImplTest extends CertificationDomainTestCase {

    @Inject
    private ResourceCertificateViewService subject;

    @Test
    public void findCertifiedResources() {
        assertThat(subject.findCertifiedResources(-34L)).isNull();
    }

    @Test
    public void findCurrentIncomingResourceCertificate() {
        assertThat(subject.findCurrentIncomingResourceCertificate(-34L)).isEmpty();
    }

    @Test
    public void findCurrentOutgoingResourceCertificate() {
        assertThat(subject.findCurrentOutgoingResourceCertificate(-34L, TestObjects.TEST_KEY_PAIR_2.getPublicKey())).isEmpty();
    }
}