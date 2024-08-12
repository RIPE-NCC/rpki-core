package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Preconditions;
import lombok.NonNull;
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
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import net.ripe.rpki.services.impl.background.RoaMetricsService;
import org.springframework.beans.factory.annotation.Value;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;


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
    public void handle(@NonNull UpdateRoaConfigurationCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());
        RoaConfiguration configuration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);

        validateEntityTag(command, configuration);
        validateAsns(command);
        validateAddedPrefixes(ca, command.getAdditions());

        roaConfigurationRepository.mergePrefixes(configuration,
                RoaConfigurationPrefix.fromData(command.getAdditions()),
                RoaConfigurationPrefix.fromData(command.getDeletions()));

        ca.markConfigurationUpdated();

        roaMetricsService.countAdded(command.getAdditions().size());
        roaMetricsService.countDeleted(command.getDeletions().size());
    }

    private void validateEntityTag(UpdateRoaConfigurationCommand command, RoaConfiguration configuration) {
        command.getIfMatch().ifPresent(ifMatch -> {
            String entityTag = configuration.convertToData().entityTag();
            if (!ifMatch.equals(entityTag)) {
                throw new EntityTagDoesNotMatchException(entityTag, ifMatch);
            }
        });
    }

    private void validateAsns(UpdateRoaConfigurationCommand command) {
        List<Asn> privateAsns = command.getAdditions().stream().map(RoaConfigurationPrefixData::getAsn)
                .filter(privateAsnRanges::contains).toList();
        if (!privateAsns.isEmpty()) {
            throw new PrivateAsnsUsedException("ROA configuration", privateAsns);
        }
    }

    private void validateAddedPrefixes(ManagedCertificateAuthority ca, Collection<RoaConfigurationPrefixData> addedPrefixes) {
        ImmutableResourceSet addedResources = addedPrefixes.stream()
            .map(RoaConfigurationPrefixData::getPrefix)
            .collect(ImmutableResourceSet.collector());
        ImmutableResourceSet uncertifiedResources = addedResources.difference(ca.getCertifiedResources());
        if (!uncertifiedResources.isEmpty()) {
            throw new NotHolderOfResourcesException(uncertifiedResources);
        }
    }
}
