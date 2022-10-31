package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedMapDifference;
import lombok.NonNull;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.server.api.services.command.DuplicateResourceException;
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;


@Handler
public class UpdateAspaConfigurationCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateAspaConfigurationCommand> {

    private final AspaConfigurationRepository aspaConfigurationRepository;

    private final IpResourceSet privateAsns;

    @Inject
    public UpdateAspaConfigurationCommandHandler(
        CertificateAuthorityRepository certificateAuthorityRepository,
        AspaConfigurationRepository aspaConfigurationRepository,
        @Value("${private.asns.ranges}") String privateAsnRanges
    ) {
        super(certificateAuthorityRepository);
        this.aspaConfigurationRepository = aspaConfigurationRepository;

        this.privateAsns = IpResourceSet.parse(privateAsnRanges);
        Preconditions.checkArgument(Iterables.all(privateAsns, a -> IpResourceType.ASN == a.getType()), "Only ASNs allowed for private ASN ranges: %s", privateAsns);
    }

    @Override
    public Class<UpdateAspaConfigurationCommand> commandType() {
        return UpdateAspaConfigurationCommand.class;
    }

    @Override
    public void handle(@NonNull UpdateAspaConfigurationCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());
        SortedMap<Asn, AspaConfiguration> entities = aspaConfigurationRepository.findByCertificateAuthority(ca);

        SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> currentConfiguration = AspaConfiguration.entitiesToMaps(entities);
        validateEntityTag(command, currentConfiguration);

        SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> updatedConfiguration = parseUpdatedConfiguration(ca, command);

        SortedMapDifference<Asn, SortedMap<Asn, AspaAfiLimit>> difference = Maps.difference(currentConfiguration, updatedConfiguration);
        if (difference.areEqual()) {
            throw new CommandWithoutEffectException(command);
        }
        for (Asn removed : difference.entriesOnlyOnLeft().keySet()) {
            aspaConfigurationRepository.remove(entities.get(removed));
        }
        for (Map.Entry<Asn, SortedMap<Asn, AspaAfiLimit>> added : difference.entriesOnlyOnRight().entrySet()) {
            aspaConfigurationRepository.add(new AspaConfiguration(ca, added.getKey(), added.getValue()));
        }
        for (Map.Entry<Asn, MapDifference.ValueDifference<SortedMap<Asn, AspaAfiLimit>>> updated : difference.entriesDiffering().entrySet()) {
            entities.get(updated.getKey()).setProviders(updated.getValue().rightValue());
        }

        ca.configurationUpdated();
    }

    private SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> parseUpdatedConfiguration(ManagedCertificateAuthority ca, UpdateAspaConfigurationCommand command) {
        SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> updatedConfiguration;
        try {
            updatedConfiguration = AspaConfigurationData.dataToMaps(command.getConfiguration());
        } catch (IllegalStateException e) {
            throw new DuplicateResourceException("duplicate ASN in ASPA configuration");
        }

        validateCustomerAsns(ca, updatedConfiguration);
        validateProviderAsns(updatedConfiguration);

        return updatedConfiguration;
    }

    private static void validateEntityTag(UpdateAspaConfigurationCommand command, SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> currentConfiguration) {
        String entityTag = AspaConfigurationData.entityTag(currentConfiguration);
        if (!entityTag.equals(command.getIfMatch())) {
            throw new EntityTagDoesNotMatchException(entityTag, command.getIfMatch());
        }
    }

    private static void validateCustomerAsns(ManagedCertificateAuthority ca, SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> updatedConfiguration) {
        IpResourceSet certifiedResources = ca.getCertifiedResources();
        IpResourceSet customerAsns = new IpResourceSet(updatedConfiguration.keySet());
        customerAsns.removeAll(certifiedResources);
        if (!customerAsns.isEmpty()) {
            throw new NotHolderOfResourcesException(customerAsns);
        }
    }

    private void validateProviderAsns(SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> configuration) {
        for (Map.Entry<Asn, SortedMap<Asn, AspaAfiLimit>> aspa : configuration.entrySet()) {
            Asn customerAsn = aspa.getKey();
            Set<Asn> providerAsns = aspa.getValue().keySet();

            if (providerAsns.contains(customerAsn)) {
                throw new DuplicateResourceException(String.format("customer %s appears in provider set %s", customerAsn, providerAsns));
            }
        }

        List<Asn> configuredPrivateProviderAsns = findAddedPrivateAsns(configuration);
        if (!configuredPrivateProviderAsns.isEmpty()) {
            throw new PrivateAsnsUsedException("ASPA configuration", configuredPrivateProviderAsns);
        }
    }

    private List<Asn> findAddedPrivateAsns(SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> configuration) {
        return configuration.values().stream()
            .flatMap(providers -> providers.keySet().stream())
            .filter(privateAsns::contains)
            .collect(Collectors.toList());
    }
}
