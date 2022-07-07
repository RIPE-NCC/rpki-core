package net.ripe.rpki.domain.alerts;

import com.google.common.collect.Lists;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
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
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static net.ripe.ipresource.IpResourceSet.parse;
import static net.ripe.rpki.commons.validation.roa.RouteValidityState.*;
import static org.assertj.core.api.Assertions.assertThat;

@Setter
@Slf4j
@Transactional
public class RoaAlertMaintenanceServiceTest extends CertificationDomainTestCase {
    private static final long HOSTED_CA_ID = 7L;

    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    private static List<AnnouncedRoute> ALL_ROUTES = Lists.newArrayList(
            // The /7 private use resource - less specific than allocation
            new AnnouncedRoute(Asn.parse("64496"), IpRange.parse("fc00::/7")),
            // The allocation
            new AnnouncedRoute(Asn.parse("64497"), IpRange.parse("fc00::/8")),
            // And two for the same AS in the first half
            new AnnouncedRoute(Asn.parse("64498"), IpRange.parse("fc20::/11")),
            new AnnouncedRoute(Asn.parse("64499"), IpRange.parse("fc20::/11")),
            // As well as in the latter half
            new AnnouncedRoute(Asn.parse("64500"), IpRange.parse("fc80::/11")),
            new AnnouncedRoute(Asn.parse("64501"), IpRange.parse("fc80::/11"))
    );

    @Autowired
    private JpaResourceCacheImpl resourceCache;

    @Autowired
    private CommandService subject;

    @Autowired
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Autowired
    private CommandAuditService commandAuditService;

    private ProductionCertificateAuthority parent;
    private CustomerCertificateAuthority child;

    @Before
    public void setUp() {
        clearDatabase();

        parent = createInitializedAllResourcesAndProductionCertificateAuthority();

        child = new CustomerCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, parent);
        certificateAuthorityRepository.add(child);
        child.createNewKeyPair(keyPairService);

        // Add an alert
        var ca = certificateAuthorityRepository.findHostedCa(HOSTED_CA_ID);
        RoaAlertConfiguration weekly = new RoaAlertConfiguration(ca, "weekly@example.org", Arrays.asList(INVALID_ASN, INVALID_LENGTH, UNKNOWN), RoaAlertFrequency.WEEKLY);
        weekly.update(ALL_ROUTES, Collections.emptyList());
        roaAlertConfigurationRepository.add(weekly);

        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/8"));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));
    }

    /**
     * Check the expected state after initial update.
     */
    @Test
    public void should_issue_certificate_for_hosted_child_certified_resources_and_keep_subscriptions() {
        assertThat(child.getCertifiedResources()).isEqualTo(parse("fc00::/8"));

        Collection<KeyPairEntity> keyPairs = child.getKeyPairs();
        assertThat(keyPairs).hasSize(1);

        Optional<IncomingResourceCertificate> certificate = child.findCurrentIncomingResourceCertificate();
        assertThat(certificate).isPresent();
        assertThat(certificate.get().getResources()).isEqualTo(parse("fc00::/8"));

        var alertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        assertThat(alertConfiguration.getIgnored())
                .map(ig -> ig.toData())
                .containsExactlyInAnyOrderElementsOf(ALL_ROUTES);

        var caLog = commandAuditService.findCommandsSinceCaVersion(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION));

        assertThat(caLog.stream().filter(msg -> msg.getSummary().contains("Updated suppressed routes")))
                .hasSize(0);
    }

    @Test
    public void should_remove_alerts_out_of_resources_when_resources_contract() {
        // Update space to the /9 which is the first half of the /8
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/9"));

        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));

        Optional<IncomingResourceCertificate> maybeCertificate = resourceCertificateRepository.findIncomingResourceCertificateBySubjectKeyPair(child.getCurrentKeyPair());
        assertThat(maybeCertificate).isPresent();

        assertThat(maybeCertificate.get().getResources()).isEqualTo(parse("fc00::/9"));

        // Has only the silences that are not in the half of the /8 no longer in the resources or are less specific.
        var alertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(HOSTED_CA_ID);
        assertThat(alertConfiguration.getIgnored())
                .map(ig -> ig.toData())
                .containsExactlyInAnyOrderElementsOf(
                        ALL_ROUTES.stream()
                                .filter(r -> !IpRange.parse("fc80::/9").contains(r.getPrefix()))
                                .collect(Collectors.toList())
                )
                .hasSize(4);

        var caLog = commandAuditService.findCommandsSinceCaVersion(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION));

        assertThat(caLog.stream().filter(msg -> msg.getCommandEvents().contains("Updated suppressed routes")))
                .hasSize(1);
    }

    private CommandStatus execute(CertificateAuthorityCommand command) {
        try {
            return subject.execute(command);
        } finally {
            entityManager.flush();
        }
    }
}
