package net.ripe.rpki.ripencc.cache;

import jakarta.persistence.EntityManager;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.security.auth.x500.X500Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class JpaResourceCacheImplTest extends CertificationDomainTestCase {

    @Autowired
    private JpaResourceCacheImpl resourceCache;

    @Autowired
    private EntityManager entityManager;

    @Before
    public void setUp() {
        inTx(this::clearDatabase);
    }

    @Test
    public void testEmptyIsEmpty() {
        assertTrue(resourceCache.hasNoMemberResources());
    }

    @Test
    public void testEmptyAfterCleaning() {
        Map<CaName, ImmutableResourceSet> m = new HashMap<>();
        m.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8"));
        m.put(CaName.fromMembershipId(2), ImmutableResourceSet.parse("11.0.0.0/8"));
        inTx(() -> resourceCache.populateCache(m));
        assertFalse(resourceCache.hasNoMemberResources());
        inTx(() -> resourceCache.clearCache());
        assertTrue(resourceCache.hasNoMemberResources());
    }

    @Test
    public void lookup_should_fail_when_production_ca_resource_are_absent() {
        Map<CaName, ImmutableResourceSet> m = new HashMap<>();
        m.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8"));
        inTx(() -> {
            resourceCache.dropCache();
            resourceCache.populateCache(m);
        });

        assertEquals(Optional.empty(), resourceCache.lookupResources(CaName.fromMembershipId(1)));
        assertEquals(Optional.empty(), resourceCache.lookupResources(CaName.fromMembershipId(2)));
    }

    @Test
    public void testLookupAfterPopulate() {
        Map<CaName, ImmutableResourceSet> m = new HashMap<>();
        m.put(CaName.fromMembershipId(1), ImmutableResourceSet.parse("10.0.0.0/8"));
        m.put(CaName.fromMembershipId(2), ImmutableResourceSet.parse("11.0.0.0/8"));
        inTx(() -> resourceCache.populateCache(m));

        assertEquals(Optional.of(ImmutableResourceSet.parse("10.0.0.0/8")), resourceCache.lookupResources(CaName.fromMembershipId(1)));
        assertEquals(Optional.of(ImmutableResourceSet.parse("11.0.0.0/8")), resourceCache.lookupResources(CaName.fromMembershipId(2)));
        assertEquals(Optional.of(ImmutableResourceSet.parse("")), resourceCache.lookupResources(CaName.fromMembershipId(3)));
    }

    @Test
    public void shouldReturnHostedCaWithDifferentResourcesOnCertificate() {
        var childName = new X500Principal("CN=child");

        inTx(() -> {
            var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
            var child = new HostedCertificateAuthority(7L, childName, UUID.randomUUID(), parent);
            issueCertificateForNewKey(parent, child, ImmutableResourceSet.parse("10.0.0.0/8"));
            resourceCache.updateEntry(CaName.of(childName), ImmutableResourceSet.parse("11.0.0.0/8"));
        });

        var result = withTx(() -> resourceCache.getHostedCasWithDifferentResourcesOnCertificates());
        assertThat(result).containsExactly(childName);
    }

    @Test
    public void shouldNotReturnHostedCaWithMatchingResourcesOnCertificate() {
        var childName = new X500Principal("CN=child");

        inTx(() -> {
            var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
            var child = new HostedCertificateAuthority(7L, childName, UUID.randomUUID(), parent);
            issueCertificateForNewKey(parent, child, ImmutableResourceSet.parse("10.0.0.0/8"));
            resourceCache.updateEntry(CaName.of(childName), ImmutableResourceSet.parse("10.0.0.0/8"));
        });

        var result = withTx(() -> resourceCache.getHostedCasWithDifferentResourcesOnCertificates());
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnHostedCaWithNoCertificate() {
        var childName = new X500Principal("CN=no-cert-child");

        inTx(() -> {
            var parent = createInitializedAllResourcesAndProductionCertificateAuthority();
            var child = new HostedCertificateAuthority(8L, childName, UUID.randomUUID(), parent);
            certificateAuthorityRepository.add(child);
            resourceCache.updateEntry(CaName.of(childName), ImmutableResourceSet.parse("10.0.0.0/8"));
        });

        var result = withTx(() -> resourceCache.getHostedCasWithDifferentResourcesOnCertificates());
        assertThat(result).containsExactly(childName);
    }

}
