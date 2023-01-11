package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Preconditions;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import net.ripe.rpki.services.impl.background.RoaMetricsService;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Handler
public class UpdateRoaConfigurationCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateRoaConfigurationCommand> {

    private final RoaConfigurationRepository roaConfigurationRepository;
    private final ImmutableResourceSet privateAsnRanges;
    private final RoaMetricsService roaMetricsService;

    @Inject
    public UpdateRoaConfigurationCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                RoaConfigurationRepository roaConfigurationRepository,
                                                @Value("${private.asns.ranges}") String privateASNS,
                                                RoaMetricsService roaMetricsService) {
        super(certificateAuthorityRepository);
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.roaMetricsService = roaMetricsService;

        this.privateAsnRanges = ImmutableResourceSet.parse(privateASNS);
        Preconditions.checkArgument(privateAsnRanges.stream().allMatch(a -> a.getType() == IpResourceType.ASN), "Only ASNs allowed for private ASN ranges: %s", privateAsnRanges);
    }


    @Override
    public Class<UpdateRoaConfigurationCommand> commandType() {
        return UpdateRoaConfigurationCommand.class;
    }

    @Override
    public void handle(UpdateRoaConfigurationCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);

        List<Asn> privateAsns = findAddedPrivateAsns(command);
        if (!privateAsns.isEmpty()) {
            throw new PrivateAsnsUsedException("ROA configuration", privateAsns);
        }

        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());
        RoaConfiguration configuration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);
        final Set<RoaConfigurationPrefix> formerPrefixes = new HashSet<>(configuration.getPrefixes());

        Collection<RoaConfigurationPrefix> addedPrefixes = RoaConfigurationPrefix.fromData(command.getAdditions());
        Collection<RoaConfigurationPrefix> deletedPrefixes = RoaConfigurationPrefix.fromData(command.getDeletions());

        validateAddedPrefixes(ca, addedPrefixes);

        configuration.addPrefix(addedPrefixes);
        configuration.removePrefix(deletedPrefixes);

        if (!deletedPrefixes.isEmpty()) {
            final Set<? extends RoaConfigurationPrefix> actualDeletable =
                deletedPrefixes.stream().filter(formerPrefixes::contains).collect(Collectors.toSet());
            roaConfigurationRepository.logRoaPrefixDeletion(configuration, actualDeletable);
        }
        roaMetricsService.countAdded(command.getAdditions().size());
        roaMetricsService.countDeleted(command.getDeletions().size());

        ca.configurationUpdated();
    }

    private void validateAddedPrefixes(ManagedCertificateAuthority ca, Collection<RoaConfigurationPrefix> addedPrefixes) {
        ImmutableResourceSet addedResources = addedPrefixes.stream()
            .map(RoaConfigurationPrefix::getPrefix)
            .collect(ImmutableResourceSet.collector());
        ImmutableResourceSet uncertifiedResources = addedResources.difference(ca.getCertifiedResources());
        if (!uncertifiedResources.isEmpty()) {
            throw new NotHolderOfResourcesException(uncertifiedResources);
        }
    }

    private List<Asn> findAddedPrivateAsns(UpdateRoaConfigurationCommand command) {
        return command.getAdditions().stream().map(RoaConfigurationPrefixData::getAsn)
                .filter(privateAsnRanges::contains)
                .collect(Collectors.toList());
    }
}
