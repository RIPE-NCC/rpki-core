package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.RoaConfigurationForPrivateASNException;
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
    private final RoaEntityService roaEntityService;
    private final IpResourceSet privateAsnRanges;
    private final RoaMetricsService roaMetricsService;

    @Inject
    public UpdateRoaConfigurationCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                RoaConfigurationRepository roaConfigurationRepository,
                                                RoaEntityService roaEntityService,
                                                @Value("${private.asns.ranges}") String privateASNS,
                                                RoaMetricsService roaMetricsService) {
        super(certificateAuthorityRepository);
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.roaEntityService = roaEntityService;
        this.roaMetricsService = roaMetricsService;

        this.privateAsnRanges = IpResourceSet.parse(privateASNS);
        Preconditions.checkArgument(Iterables.all(privateAsnRanges, a -> IpResourceType.ASN.equals(a.getType())), "Only ASNs allowed for private ASN ranges: %s", privateAsnRanges);
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
            throw new RoaConfigurationForPrivateASNException(privateAsns);
        }

        HostedCertificateAuthority ca = lookupHostedCA(command.getCertificateAuthorityVersionedId().getId());
        RoaConfiguration configuration = roaConfigurationRepository.getOrCreateByCertificateAuthority(ca);
        final Set<RoaConfigurationPrefix> formerPrefixes = new HashSet<>(configuration.getPrefixes());
        configuration.addPrefix(RoaConfigurationPrefix.fromData(command.getAdditions()));
        final Collection<? extends RoaConfigurationPrefix> deletedPrefixes = RoaConfigurationPrefix.fromData(command.getDeletions());
        configuration.removePrefix(deletedPrefixes);
        if (!deletedPrefixes.isEmpty()) {
            final Set<? extends RoaConfigurationPrefix> actualDeletable =
                deletedPrefixes.stream().filter(formerPrefixes::contains).collect(Collectors.toSet());
            roaEntityService.logRoaPrefixDeletion(configuration, actualDeletable);
        }
        roaMetricsService.countAdded(command.getAdditions().size());
        roaMetricsService.countDeleted(command.getDeletions().size());

        roaEntityService.roaConfigurationUpdated(ca);
    }

    private List<Asn> findAddedPrivateAsns(UpdateRoaConfigurationCommand command) {
        return command.getAdditions().stream().map(RoaConfigurationPrefixData::getAsn)
                .filter(privateAsnRanges::contains)
                .collect(Collectors.toList());
    }
}
