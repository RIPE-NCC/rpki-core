package net.ripe.rpki.core.read.services.ca;

import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class CertificateAuthorityViewServiceImplTest extends CertificationDomainTestCase {

    @Inject
    private CertificateAuthorityViewService subject;

    @Test
    public void findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth() {
        assertThat(subject.findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).isEmpty();
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan() {
        final Instant oldestCreationTime = Instant.now().minus(Duration.standardDays(10));
        assertThat(subject.findAllHostedCasWithCurrentKeyOnlyAndOlderThan(HostedCertificateAuthority.class, oldestCreationTime, Optional.of(1000))).isEmpty();
    }

    @Test
    public void findNonHostedPublisherRepositories() {
        clearDatabase();
        ProductionCertificateAuthority parent = createInitialisedProdCaWithRipeResources();
        X500Principal principal = new X500Principal("CN=non-hosted");
        NonHostedCertificateAuthority nonHostedCertificateAuthority = new NonHostedCertificateAuthority(123L, principal,
                ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
                parent);
        certificateAuthorityRepository.add(nonHostedCertificateAuthority);

        assertThat(subject.findNonHostedPublisherRepositories(principal)).isEmpty();
    }

    @Test
    public void findNonHostedPublisherRepositoriesFailed() {
        X500Principal principal = new X500Principal("CN=non-hosted");
        assertThatThrownBy(() -> subject.findNonHostedPublisherRepositories(principal))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("non-hosted CA '" + principal.getName() + "' not found");

    }
}
