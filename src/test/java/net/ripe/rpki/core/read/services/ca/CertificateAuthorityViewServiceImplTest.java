package net.ripe.rpki.core.read.services.ca;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.dto.DelegatedCa;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.util.Crypto;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;

import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import jakarta.transaction.Transactional;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class CertificateAuthorityViewServiceImplTest extends CertificationDomainTestCase {
    public static final ImmutableResourceSet CHILD_CA_RESOURCES = ImmutableResourceSet.parse("10.0.0.0/8");

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
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        issueCertificateForNewKey(parent, child, CHILD_CA_RESOURCES);

        // CAs are too new to be selected
        final Instant tenDaysOld = Instant.now().minus(Duration.standardDays(10));
        assertThat(subject.findManagedCasEligibleForKeyRoll(HostedCertificateAuthority.class, tenDaysOld, Optional.of(1000))).isEmpty();

        // Child CA should be selected when selecting all hosted CAs
        assertThat(subject.findManagedCasEligibleForKeyRoll(ManagedCertificateAuthority.class, Instant.now().plus(Duration.standardSeconds(10)), Optional.of(1000))
        ).anyMatch(ca -> ca.getName().equals(CHILD_CA_NAME));

        // And method filters by type - Hosted \notin AllResources
        assertThat(subject.findManagedCasEligibleForKeyRoll(AllResourcesCertificateAuthority.class, Instant.now(), Optional.of(1000))
        ).noneMatch(ca -> ca.getName().equals(CHILD_CA_NAME));
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan_without_keypair() {
        clearDatabase();
        var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        // CA without keypair or certificate
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        certificateAuthorityRepository.add(child);

        assertThat(subject.findManagedCasEligibleForKeyRoll(HostedCertificateAuthority.class, Instant.now(), Optional.of(1000))
        ).anyMatch(ca -> ca.getName().equals(CHILD_CA_NAME));
    }

    @Test
    public void findAllHostedCasWithCurrentKeyOnlyAndOlderThan_has_pending() {
        clearDatabase();
        var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        var child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        issueCertificateForNewKey(parent, child, CHILD_CA_RESOURCES);

        // Add a new (NEW) key -> ca will not be selected
        KeyPairEntity acaKeyPair = keyPairService.createKeyPairEntity();
        child.addKeyPair(acaKeyPair);

        assertThat(subject.findManagedCasEligibleForKeyRoll(HostedCertificateAuthority.class, Instant.now(), Optional.of(1000))
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

    /**
     * The {@link CertificateAuthorityViewService#getCaStats()} function makes assumptions about a number of constant
     * strings in the audit log. Check this invariant.
     */

    @Test
    public void testGetCaEventStatsMagicConstants() {
        assertThat(UpdateRoaConfigurationCommand.class.getSimpleName()).isEqualTo("UpdateRoaConfigurationCommand");
        assertThat(ActivateHostedCertificateAuthorityCommand.class.getSimpleName()).isEqualTo("ActivateHostedCertificateAuthorityCommand");
        assertThat(ActivateNonHostedCertificateAuthorityCommand.class.getSimpleName()).isEqualTo("ActivateNonHostedCertificateAuthorityCommand");
        assertThat(DeleteCertificateAuthorityCommand.class.getSimpleName()).isEqualTo("DeleteCertificateAuthorityCommand");
        assertThat(DeleteNonHostedCertificateAuthorityCommand.class.getSimpleName()).isEqualTo("DeleteNonHostedCertificateAuthorityCommand");
    }

    @Test
    public void findDelegatedCas_withoutLastProvisionedAt() {
        clearDatabase();
        ProductionCertificateAuthority parent = createInitialisedProdCaWithRipeResources();
        PublicKey publicKey = TestObjects.createTestKeyPair().getPublicKey();
        var delegatedCa = new X500Principal("CN=delegated");
        var nonHostedCa = new NonHostedCertificateAuthority(
            123L, delegatedCa,
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, parent
        );
        PublicKeyEntity publicKeyEntity = nonHostedCa.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(), List.of());
        certificateAuthorityRepository.add(nonHostedCa);
        entityManager.flush();

        List<DelegatedCa> result = subject.findDelegatedCas();
        assertThat(result).hasSize(1);

        var ca = result.getFirst();
        assertThat(ca.caName()).isEqualTo(delegatedCa.getName());
        assertThat(ca.keyIdentifier()).isEqualTo(Crypto.getKeyIdentifier(publicKey.getEncoded()));
        assertThat(ca.lastProvisionedAt()).isEmpty();
    }

    @Test
    public void findDelegatedCas_withLastProvisionedAt() {
        clearDatabase();
        ProductionCertificateAuthority parent = createInitialisedProdCaWithRipeResources();
        PublicKey publicKey = TestObjects.createTestKeyPair().getPublicKey();
        var delegatedCa = new X500Principal("CN=delegated");
        var nonHostedCa = new NonHostedCertificateAuthority(
            123L, delegatedCa,
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, parent
        );
        PublicKeyEntity publicKeyEntity = nonHostedCa.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        publicKeyEntity.setLatestIssuanceRequest(new RequestedResourceSets(), List.of());
        certificateAuthorityRepository.add(nonHostedCa);
        entityManager.flush();

        // Insert an audit log entry with request_message_type = 'issue_response' to simulate a provisioned-at timestamp
        entityManager.createNativeQuery("""
            INSERT INTO provisioning_audit_log (
                id, version, created_at, updated_at, non_hosted_ca_uuid, request_message_type,
                provisioning_cms_object, principal, summary, executiontime, entry_uuid
            ) VALUES (
                nextval('seq_all'), 0, NOW(), NOW(), :uuid, 'issue_response',
                :cms, 'test', 'test summary', NOW(), :entryUuid
            )
            """)
            .setParameter("uuid", nonHostedCa.getUuid())
            .setParameter("cms", new byte[]{1, 2, 3})
            .setParameter("entryUuid", UUID.randomUUID())
            .executeUpdate();
        entityManager.flush();

        List<DelegatedCa> result = subject.findDelegatedCas();
        assertThat(result).hasSize(1);

        var ca = result.getFirst();
        assertThat(ca.caName()).isEqualTo(delegatedCa.getName());
        assertThat(ca.keyIdentifier()).isEqualTo(Crypto.getKeyIdentifier(publicKey.getEncoded()));
        assertThat(ca.lastProvisionedAt()).isPresent();
    }
}
