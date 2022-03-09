package net.ripe.rpki.core.read.services.ca;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class CertificateAuthorityViewServiceImplTest extends CertificationDomainTestCase {

    @Inject
    private CertificateAuthorityViewService subject;

    @Test
    public void findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth() {
        assertThat(subject.findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).isEmpty();
    }

    @Test
    public void findAllHostedCasWithOldKeyPairs() {
        final Instant oldestCreationTime = Instant.now().minus(Duration.standardDays(10));
        assertThat(subject.findAllHostedCasWithKeyPairsOlderThan(oldestCreationTime)).isEmpty();
    }
}
