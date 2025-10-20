package net.ripe.rpki.services.impl.handlers;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedMapDifference;
import lombok.NonNull;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.server.api.services.command.IllegalResourceException;
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import org.springframework.beans.factory.annotation.Value;

import jakarta.inject.Inject;
import java.util.*;


@Handler
public class UpdateAspaConfigurationCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateAspaConfigurationCommand> {

    private final AspaConfigurationRepository aspaConfigurationRepository;

    private final ImmutableResourceSet privateAsns;

    @Inject
    public UpdateAspaConfigurationCommandHandler(
        CertificateAuthorityRepository certificateAuthorityRepository,
        AspaConfigurationRepository aspaConfigurationRepository,
        @Value("${private.asns.ranges}") String privateAsnRanges
    ) {
        super(certificateAuthorityRepository);
        this.aspaConfigurationRepository = aspaConfigurationRepository;

        this.privateAsns = ImmutableResourceSet.parse(privateAsnRanges);
        Preconditions.checkArgument(privateAsns.stream().allMatch(a -> IpResourceType.ASN == a.getType()), "Only ASNs allowed for private ASN ranges: %s", privateAsns);
    }

    @Override
    public Class<UpdateAspaConfigurationCommand> commandType() {
        return UpdateAspaConfigurationCommand.class;
    }

    @Override
    public void handle(@NonNull UpdateAspaConfigurationCommand command, CommandStatus commandStatus) {
        validateUpdateAspaConfigurationCommand(command);


        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());
        SortedMap<Asn, AspaConfiguration> entities = aspaConfigurationRepository.findByCertificateAuthority(ca);

        SortedMap<Asn, SortedSet<Asn>> currentConfiguration = AspaConfiguration.entitiesToMaps(entities);
        validateEntityTag(command, currentConfiguration);

        SortedMap<Asn, SortedSet<Asn>> updatedConfiguration = parseUpdatedConfiguration(ca, command);

        SortedMapDifference<Asn, SortedSet<Asn>> difference = Maps.difference(currentConfiguration, updatedConfiguration);
        if (difference.areEqual()) {
            throw new CommandWithoutEffectException(command);
        }
        for (Asn removed : difference.entriesOnlyOnLeft().keySet()) {
            aspaConfigurationRepository.remove(entities.get(removed));
        }
        for (Map.Entry<Asn, SortedSet<Asn>> added : difference.entriesOnlyOnRight().entrySet()) {
            aspaConfigurationRepository.add(new AspaConfiguration(ca, added.getKey(), added.getValue()));
        }
        for (Map.Entry<Asn, MapDifference.ValueDifference<SortedSet<Asn>>> updated : difference.entriesDiffering().entrySet()) {
            entities.get(updated.getKey()).setProviders(updated.getValue().rightValue());
        }

        ca.markConfigurationUpdated();
    }

    private void validateUpdateAspaConfigurationCommand(UpdateAspaConfigurationCommand command) {
        var configuration = command.getNewConfigurations();
        if (configuration.stream().map(AspaConfigurationData::getCustomerAsn).distinct().count() != configuration.size()) {
            throw new IllegalResourceException("duplicate customer ASN in ASPA configuration");
        }

        if (configuration.stream().anyMatch(ac -> ac.getProviders().stream().distinct().count() != ac.getProviders().size())) {
            throw new IllegalResourceException("duplicate provider ASN in ASPA configuration");
        }

        if (configuration.stream().anyMatch(ac -> ac.getProviders().isEmpty())) {
            throw new IllegalResourceException("One of the configured ASPAs does not have providers");
        }
    }

    private SortedMap<Asn, SortedSet<Asn>> parseUpdatedConfiguration(ManagedCertificateAuthority ca, UpdateAspaConfigurationCommand command) {
        SortedMap<Asn, SortedSet<Asn>> updatedConfiguration;
        try {
            updatedConfiguration = AspaConfigurationData.dataToMaps(command.getNewConfigurations());
        } catch (IllegalStateException e) {
            throw new IllegalResourceException("duplicate ASN in ASPA configuration");
        }

        validateCustomerAsns(ca, updatedConfiguration);
        validateProviderAsns(updatedConfiguration);

        return updatedConfiguration;
    }

    private static void validateEntityTag(UpdateAspaConfigurationCommand command, SortedMap<Asn, SortedSet<Asn>> currentConfiguration) {
        String entityTag = AspaConfigurationData.entityTag(currentConfiguration);
        if (!entityTag.equals(command.getIfMatch())) {
            throw new EntityTagDoesNotMatchException(entityTag, command.getIfMatch());
        }
    }

    private static void validateCustomerAsns(ManagedCertificateAuthority ca, SortedMap<Asn, SortedSet<Asn>> updatedConfiguration) {
        ImmutableResourceSet certifiedResources = ca.getCertifiedResources();
        ImmutableResourceSet uncertifiedAsns = ImmutableResourceSet.of(updatedConfiguration.keySet()).difference(certifiedResources);
        if (!uncertifiedAsns.isEmpty()) {
            throw new NotHolderOfResourcesException(uncertifiedAsns);
        }
    }

    private void validateProviderAsns(SortedMap<Asn, SortedSet<Asn>> configuration) {
        for (Map.Entry<Asn, SortedSet<Asn>> aspa : configuration.entrySet()) {
            Asn customerAsn = aspa.getKey();
            Set<Asn> providerAsns = aspa.getValue();

            if (providerAsns.contains(customerAsn)) {
                throw new IllegalResourceException(String.format("customer %s appears in provider set %s", customerAsn, providerAsns));
            }
        }

        List<Asn> configuredPrivateProviderAsns = findAddedPrivateAsns(configuration);
        if (!configuredPrivateProviderAsns.isEmpty()) {
            throw new PrivateAsnsUsedException("ASPA configuration", configuredPrivateProviderAsns);
        }
    }

    private List<Asn> findAddedPrivateAsns(SortedMap<Asn, SortedSet<Asn>> configuration) {
        return configuration.values().stream()
                .flatMap(Collection::stream)
                .filter(privateAsns::contains).toList();
    }
}
