package net.ripe.rpki.core.read.services.ca;

import lombok.var;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.*;
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
    public static final IpResourceSet CHILD_CA_RESOURCES = IpResourceSet.parse("10.0.0.0/8");
    private static final long HOSTED_CA_ID = 7L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    @Inject
    private CertificateAuthorityViewService subject;

    @Test
    public void findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth() {
        assertThat(subject.findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).isEmpty();
    }
    @Test
    public void findAllPublisherRequestsFromNonHostedCAs(){
        assertThat(subject.findAllPublisherRequestsFromNonHostedCAs()).isEmpty();
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan_with_keypair() {
        clearDatabase();
        var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, parent);
        issueCertificateForNewKey(parent, child, CHILD_CA_RESOURCES);

        // CAs are too new to be selected
        final Instant tenDaysOld = Instant.now().minus(Duration.standardDays(10));
        assertThat(subject.findHostedCasEligibleForKeyRoll(HostedCertificateAuthority.class, tenDaysOld, Optional.of(1000))).isEmpty();

        // Child CA should be selected when selecting all hosted CAs
        assertThat(subject.findHostedCasEligibleForKeyRoll(ManagedCertificateAuthority.class, Instant.now().plus(Duration.standardSeconds(10)), Optional.of(1000))
        ).anyMatch(ca -> ca.getName().equals(CHILD_CA_NAME));

        // And method filters by type - Hosted \notin AllResources
        assertThat(subject.findHostedCasEligibleForKeyRoll(AllResourcesCertificateAuthority.class, Instant.now(), Optional.of(1000))
        ).noneMatch(ca -> ca.getName().equals(CHILD_CA_NAME));
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan_without_keypair() {
        clearDatabase();
        var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        // CA without keypair or certificate
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, parent);
        certificateAuthorityRepository.add(child);

        assertThat(subject.findHostedCasEligibleForKeyRoll(HostedCertificateAuthority.class, Instant.now(), Optional.of(1000))
        ).anyMatch(ca -> ca.getName().equals(CHILD_CA_NAME));
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan_has_pending() {
        clearDatabase();
        var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, parent);
        issueCertificateForNewKey(parent, child, CHILD_CA_RESOURCES);

        // Add a new (NEW) key -> ca will not be selected
        KeyPairEntity acaKeyPair = keyPairService.createKeyPairEntity();
        child.addKeyPair(acaKeyPair);

        assertThat(subject.findHostedCasEligibleForKeyRoll(HostedCertificateAuthority.class, Instant.now(), Optional.of(1000))
        ).isEmpty();
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
