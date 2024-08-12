package net.ripe.rpki.core.read.services.ca;

import com.google.common.collect.Lists;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.ripencc.cache.JpaResourceCacheImpl;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.CaStatRoaEvent;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.ripe.ipresource.ImmutableResourceSet.parse;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class CertificateAuthorityViewServiceStatisticsTest extends CertificationDomainTestCase {
    private static final long HOSTED_CA_ID = 8L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    // Needs to contain non-private ASNs because command is validated
    private static final List<RoaConfigurationPrefix> ALL_ROA_CONFIGURATIONS = Lists.newArrayList(
            // The allocation
            new RoaConfigurationPrefix(Asn.parse("1"), IpRange.parse("fc00::/8")),
            // And two for the same AS in the first half
            new RoaConfigurationPrefix(Asn.parse("2"), IpRange.parse("fc20::/11")),
            new RoaConfigurationPrefix(Asn.parse("3"), IpRange.parse("fc20::/11")),
            // As well as in the latter half
            new RoaConfigurationPrefix(Asn.parse("4"), IpRange.parse("fc80::/11")),
            new RoaConfigurationPrefix(Asn.parse("5"), IpRange.parse("fc80::/11"))
    );

    @Autowired
    private JpaResourceCacheImpl resourceCache;

    @Autowired
    private RoaConfigurationRepository roaConfigurationRepository;

    @Autowired
    private CertificateAuthorityViewService subject;
    private ProductionCertificateAuthority parent;
    private HostedCertificateAuthority child;

    @Before
    public void setUp() {
        clearDatabase();

        parent = createInitializedAllResourcesAndProductionCertificateAuthority();

        child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        certificateAuthorityRepository.add(child);

        // Add the ROA configuration
        var ca = certificateAuthorityRepository.findManagedCa(HOSTED_CA_ID);
        var roaConfiguration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);
        roaConfigurationRepository.addPrefixes(roaConfiguration, ALL_ROA_CONFIGURATIONS);

        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/8"));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));
    }

    private Pair<X500Principal, Long> createAnotherCa(int roaCount) {
        var randomId = HOSTED_CA_ID + new SecureRandom().nextLong(1L << 60);
        // Add another CA with ROAs
        final var secondChildCaName = new X500Principal("CN=" + randomId);

        var secondChild = new HostedCertificateAuthority(randomId, secondChildCaName, UUID.randomUUID(), parent);
        certificateAuthorityRepository.add(secondChild);

        // Add the ROA configuration
        var ca = certificateAuthorityRepository.findManagedCa(randomId);
        var roaConfiguration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);

        var randomRoas = IntStream.range(0, roaCount)
                .mapToObj(i -> new RoaConfigurationPrefix(Asn.parse(Integer.toString(65443 + i)), IpRange.parse("192.0.2.0/24"))
                ).collect(Collectors.toList());

        roaConfigurationRepository.addPrefixes(roaConfiguration, randomRoas);

        resourceCache.updateEntry(CaName.of(secondChildCaName), parse("192.0.2.0/24"));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(randomId, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));

        return Pair.of(secondChildCaName, randomId);
    }

    @Test
    public void testGetCaStat() {
        assertThat(subject.getCaStats())
                .hasSize(1)
                .allMatch(ca -> ca.caName.equals(CHILD_CA_NAME.getName()));

        var second = createAnotherCa(42);
        // Should have two CAs, one of which has 42 ROAs
        assertThat(subject.getCaStats())
                .hasSize(2)
                .anyMatch(thatCa -> second.getKey().getName().equals(thatCa.caName) && thatCa.roas == 42);

        var third = createAnotherCa(0);
        assertThat(subject.getCaStats())
                .hasSize(3)
                .anyMatch(thatCa -> third.getKey().getName().equals(thatCa.caName) && thatCa.roas == 0);

        // Clear -> 0
        clearDatabase();
        assertThat(subject.getCaStats()).isEmpty();
    }

    @Test
    public void testGetCaStatEvents() {
        // Initially we have the all resources CA
        assertThat(subject.getCaStatEvents())
                .hasSize(0);

        // Deletion is tracked
        commandService.execute(new UpdateRoaConfigurationCommand(
                child.getVersionedId(), Optional.empty(),
                List.of(),
                ALL_ROA_CONFIGURATIONS.stream().map(ca -> ca.toData()).collect(Collectors.toList()))
        );

        assertThat(subject.getCaStatEvents())
                .asInstanceOf(InstanceOfAssertFactories.list(CaStatRoaEvent.class))
                .hasSize(1)
                // recall: DTO uses a nullable integer
                .anyMatch(elem -> child.getName().getName().equals(elem.caName) && Integer.valueOf(ALL_ROA_CONFIGURATIONS.size()).equals(elem.roasDeleted));

        // (Re-addition) is tracked
        commandService.execute(new UpdateRoaConfigurationCommand(
                child.getVersionedId(), Optional.empty(),
                ALL_ROA_CONFIGURATIONS.stream().map(ca -> ca.toData()).collect(Collectors.toList()),
                List.of())
        );
        assertThat(subject.getCaStatEvents())
                .asInstanceOf(InstanceOfAssertFactories.list(CaStatRoaEvent.class))
                .hasSize(2)
                .anyMatch(elem -> child.getName().getName().equals(elem.caName) && Integer.valueOf(ALL_ROA_CONFIGURATIONS.size()).equals(elem.roasAdded));

        // Clear -> 0
        clearDatabase();
        assertThat(subject.getCaStats()).isEmpty();
    }
}