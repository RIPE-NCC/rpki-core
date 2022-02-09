package net.ripe.rpki.services.impl.background;

import com.google.common.collect.ImmutableMap;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.CertificateAuthorityModificationCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class RoaConfigUpdaterTest {

    @Mock
    private CertificateAuthorityViewService certificateService;

    @Mock
    private CommandService commandService;

    @Mock
    private RoaConfigurationRepository roaConfigurationRepository;

    @InjectMocks
    private RoaConfigUpdater subject;

    @Test
    public void shouldDoNothingIfThereAreNothingToUpdate() {

        List<RoaConfigurationRepository.RoaConfigurationPerCa> perCaRoaConfiguration = new ArrayList<>();
        given(roaConfigurationRepository.findAllPerCa()).willReturn(perCaRoaConfiguration);

        subject.updateRoaConfig(Collections.emptyMap());
        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldReturnLostResourcesWithRemovedEntry() throws Exception {
        final CaName caName1 = CaName.of(10L);
        final CaName caName2 = CaName.of(20L);
        List<RoaConfigurationRepository.RoaConfigurationPerCa> perCaRoaConfiguration = Arrays.asList(
                new RoaConfigurationRepository.RoaConfigurationPerCa(1L, caName1, Asn.parse("AS11"),
                        IpResourceRange.parse("9.0.0.0/8"), 8),
                new RoaConfigurationRepository.RoaConfigurationPerCa(2L, caName2, Asn.parse("AS12"),
                        IpResourceRange.parse("10.0.0.0/8"),8));
        given(roaConfigurationRepository.findAllPerCa()).willReturn(perCaRoaConfiguration);

        ImmutableMap<CaName, IpResourceSet> certifiableResources = ImmutableMap.of(caName2, IpResourceSet.parse("10.0.0.0/8, AS59946"));

        CertificateAuthorityData ca1 = mockCaData(1L);
        given(certificateService.findCertificateAuthority(eq(1L))).willReturn(ca1);

        subject.updateRoaConfig(certifiableResources);

        ArgumentCaptor<CertificateAuthorityModificationCommand> commandCaptor =
                ArgumentCaptor.forClass(CertificateAuthorityModificationCommand.class);

        verify(commandService, times(2)).execute(commandCaptor.capture());

        List<CertificateAuthorityModificationCommand> commands = commandCaptor.getAllValues();

        assertEquals(2, commands.size());
        assertEquals("UpdateRoaAlertIgnoredAnnouncedRoutesCommand"  , commands.get(0).getCommandType()   );
        assertEquals("UpdateRoaConfigurationCommand", commands.get(1).getCommandType());
        assertEquals(new VersionedId(1L), commands.get(0).getCertificateAuthorityVersionedId());
        assertEquals(new VersionedId(1L), commands.get(1).getCertificateAuthorityVersionedId());

        List<RoaConfigurationPrefixData> deletions = ((UpdateRoaConfigurationCommand) commands.get(1)).getDeletions();
        assertEquals(1, deletions.size());
        assertEquals(Asn.parse("AS11"), deletions.get(0).getAsn());
        assertEquals(new RoaPrefix(IpRange.parse("9.0.0.0/8"),8), deletions.get(0).getRoaPrefix());

    }

    private CertificateAuthorityData mockCaData(Long id) {
        CertificateAuthorityData caData = mock(CertificateAuthorityData.class);
        given(caData.getVersionedId()).willReturn(new VersionedId(id));
        return caData;
    }

}