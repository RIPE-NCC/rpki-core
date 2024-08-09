package net.ripe.rpki.ripencc;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.bgpris.BgpRisEntryRepositoryBean;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.rest.service.Rest;
import net.ripe.rpki.ripencc.cache.JpaResourceCacheImpl;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.security.auth.x500.X500Principal;
import java.util.*;
import static net.ripe.ipresource.ImmutableResourceSet.parse;
import static net.ripe.rpki.rest.service.RestService.API_URL_PREFIX;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@Transactional
@AutoConfigureMockMvc
@AutoConfigureWebMvc
public class RoaIT extends CertificationDomainTestCase {
    private static final long HOSTED_CA_ID = 454L;
    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");
    public static final ImmutableResourceSet CHILD_CA_RESOURCES = ImmutableResourceSet.parse("fc00::/12, 192.168.0.0/16");
    private static final BgpRisEntry BGP_RIS_ENTRY_1 = new BgpRisEntry(Asn.parse("AS3549"), IpRange.parse("fc00::/12"), 100);
    private static final BgpRisEntry BGP_RIS_ENTRY_2 = new BgpRisEntry(Asn.parse("AS3549"), IpRange.parse("192.168.0.0/16"), 100);
    @Autowired
    private JpaResourceCacheImpl resourceCache;
    @Autowired
    private CommandService subject;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BgpRisEntryRepositoryBean bgpRisEntryRepository;
    private ProductionCertificateAuthority parent;
    private HostedCertificateAuthority child;

    @Before
    public void setUp() throws Exception {
        clearDatabase();
        parent = createInitializedAllResourcesAndProductionCertificateAuthority();
        child = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        issueCertificateForNewKey(parent, child, CHILD_CA_RESOURCES);
        certificateAuthorityRepository.add(child);
        resourceCache.updateEntry(CaName.of(CHILD_CA_NAME), parse("fc00::/12, 192.168.0.0/16"));
        execute(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(HOSTED_CA_ID, VersionedId.INITIAL_VERSION), Integer.MAX_VALUE));
        bgpRisEntryRepository.resetEntries(Arrays.asList(BGP_RIS_ENTRY_1));
        // Add initial ROA first
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/" + child.getName() + "/roas/publish")
                        .content("{ " +
                                "\"added\" : [{\"asn\" : \"AS3549\", \"prefix\" : \"fc00::/12\", \"maximalLength\" : \"12\"}], " +
                                "\"deleted\" : [] " +
                                "}"))
                .andExpect(status().is(204));
    }

    @Test
    public void itIsNotPossibleToStageExistingRoaWhenAnnouncementNotFound() throws Exception {
        // All of the sudden we do not see initial announcement anymore, it has changed.
        bgpRisEntryRepository.resetEntries(Arrays.asList(BGP_RIS_ENTRY_2));

        // Ok, announcement has changed, let's see if we can alter existing ROA and add new to cover new announcement, staging
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/" + child.getName() + "/roas/stage")
                        .content("[{\"asn\" : \"AS3549\", \"prefix\" : \"fc00::/12\", \"maximalLength\" : \"14\"}," +
                                "{\"asn\" : \"AS3549\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\" : \"16\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS3549"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("100"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("UNKNOWN"))
                .andExpect(jsonPath("$.[0].futureState").value("VALID"))
                .andExpect(jsonPath("$.[0].affectedByChange").value("true"));
    }

    @Test
    public void itIsPossibleToUpdateExistingRoaWhenAnnouncementNotFound() throws Exception {
        // All of the sudden we do not see initial announcement anymore, it has changed.
        bgpRisEntryRepository.resetEntries(Arrays.asList(BGP_RIS_ENTRY_2));

        // Is it still possible to update initial ROA?
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/" + child.getName() + "/roas/publish")
                        .content("{ " +
                                "\"added\" : [{\"asn\" : \"AS3549\", \"prefix\" : \"fc00::/12\", \"maximalLength\" : \"14\"}], " +
                                "\"deleted\" : [{\"asn\" : \"AS3549\", \"prefix\" : \"fc00::/12\", \"maximalLength\" : \"12\"}] " +
                                "}"))
                .andExpect(status().is(204));

        // Initial ROA should not be VALID anyways.
        mockMvc.perform(Rest.get(API_URL_PREFIX + "/" + child.getName() + "/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS3549"))
                .andExpect(jsonPath("$.[0].prefix").value("fc00::/12"))
                .andExpect(jsonPath("$.[0]._numberOfValidsCaused").value("0"))
                .andExpect(jsonPath("$.[0]._numberOfInvalidsCaused").value("0"))
                .andExpect(jsonPath("$.[0].maximalLength").value("14"));
    }

    @Test
    public void roaIsValidatingAgainAfterCorrespondingAnnouncementIsVisibleAgain() throws Exception {
        // First announcement is visible again, so both announcements are visible now.
        bgpRisEntryRepository.resetEntries(Arrays.asList(BGP_RIS_ENTRY_1, BGP_RIS_ENTRY_2));

        // It is expected to have two ROAs and both valid
        mockMvc.perform(Rest.get(API_URL_PREFIX + "/" + child.getName() + "/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS3549"))
                .andExpect(jsonPath("$.[0].prefix").value("fc00::/12"))
                .andExpect(jsonPath("$.[0]._numberOfValidsCaused").value("1"))
                .andExpect(jsonPath("$.[0]._numberOfInvalidsCaused").value("0"))
                .andExpect(jsonPath("$.[0].maximalLength").value("12"));
    }

    protected CommandStatus execute(CertificateAuthorityCommand command) {
        try {
            return subject.execute(command);
        } finally {
            entityManager.flush();
        }
    }

    private static Collection<BgpRisEntry> entries(BgpRisEntry... entries) {
        return new HashSet<>(Arrays.asList(entries));
    }
}