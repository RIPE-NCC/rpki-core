package net.ripe.rpki.domain.roa;

import com.google.common.collect.Lists;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.ripencc.cache.JpaResourceCacheImpl;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.security.auth.x500.X500Principal;
import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.ipresource.ImmutableResourceSet.parse;
import static org.assertj.core.api.Assertions.assertThat;

@Setter
@Slf4j
@Transactional
public class RoaConfigurationMaintenanceServiceTest extends CertificationDomainTestCase {
    private static final long HOSTED_CA_ID = 8L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    private static List<RoaConfigurationPrefix> ALL_ROA_CONFIGURATIONS = Lists.newArrayList(
            // The allocation
            new RoaConfigurationPrefix(Asn.parse("64497"), IpRange.parse("fc00::/8")),
            // And two for the same AS in the first half
            new RoaConfigurationPrefix(Asn.parse("64498"), IpRange.parse("fc20::/11")),
            new RoaConfigurationPrefix(Asn.parse("64499"), IpRange.parse("fc20::/11")),
            // As well as in the latter half
            new RoaConfigurationPrefix(Asn.parse("64500"), IpRange.parse("fc80::/11")),
            new RoaConfigurationPrefix(Asn.parse("64501"), IpRange.parse("fc80::/11"))
    );

    @Autowired
    private JpaResourceCacheImpl resourceCache;

    @Autowired
    private RoaConfigurationRepository roaConfigurationRepository;

    @Autowired
    private CommandAuditService commandAuditService;

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
        roaConfiguration.addPrefix(ALL_ROA_CONFIGURATIONS);

        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/8"));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));
    }

    /**
     * Check the expected state after initial update.
     */
    @Test
    public void should_issue_certificate_for_hosted_child_certified_resources_and_keep_roa_configurations() {
        assertThat(child.getCertifiedResources()).isEqualTo(parse("fc00::/8"));

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).hasSize(1);

        Optional<IncomingResourceCertificate> certificate = child.findCurrentIncomingResourceCertificate();
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("fc00::/8"));

        var roaConfiguration = roaConfigurationRepository.getOrCreateByCertificateAuthority(child);
        assertThat(roaConfiguration.getPrefixes())
                .containsExactlyInAnyOrderElementsOf(ALL_ROA_CONFIGURATIONS)
                .hasSize(5);

        var caLog = commandAuditService.findCommandsSinceCaVersion(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION));

        assertThat(caLog.stream().filter(msg -> msg.getSummary().contains("Updated ROA"))).isEmpty();
    }

    @Test
    public void should_remove_roa_configurations_out_of_resources_when_resources_contract() {
        // Update space to the /9 which is the first half of the /8
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/9"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));

        Optional<IncomingResourceCertificate> maybeCertificate = resourceCertificateRepository.findIncomingResourceCertificateBySubjectKeyPair(child.getCurrentKeyPair());
        assertThat(maybeCertificate).isPresent();

        assertThat(maybeCertificate.get().getResources()).isEqualTo(parse("fc00::/9"));

        // Has only the silences that are not in the half of the /8 no longer in the resources.
        var roaConfiguration = roaConfigurationRepository.getOrCreateByCertificateAuthority(child);
        assertThat(roaConfiguration.getPrefixes())
                .containsExactlyInAnyOrderElementsOf(
                        ALL_ROA_CONFIGURATIONS.stream()
                                .filter(r -> IpRange.parse("fc00::/9").contains(r.getPrefix())).toList()
                )
                .hasSize(2);

        var caLog = commandAuditService.findCommandsSinceCaVersion(new VersionedId(HOSTED_CA_ID, -2));

        assertThat(caLog.stream().filter(msg -> msg.getCommandEvents().contains("Updated ROA")))
                .hasSize(1);
    }

    @Test
    public void should_remove_roa_prefixes_when_resources_are_removed() {
        // Certificate loses it's resources
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse(""));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));

        assertThat(roaConfigurationRepository.findByCertificateAuthority(child))
                .hasValueSatisfying(config -> assertThat(config.getPrefixes()).isEmpty());
    }
}
