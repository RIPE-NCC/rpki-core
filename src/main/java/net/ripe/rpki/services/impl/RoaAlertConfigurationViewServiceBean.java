package net.ripe.rpki.services.impl;

import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional
public class RoaAlertConfigurationViewServiceBean implements RoaAlertConfigurationViewService {

    private final RoaAlertConfigurationRepository repository;

    @Inject
    public RoaAlertConfigurationViewServiceBean(RoaAlertConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public RoaAlertConfigurationData findRoaAlertSubscription(long caId) {
        RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(caId);
        return configuration == null ? null : configuration.toData();
    }

    @Override
    public List<RoaAlertConfigurationData> findAll() {
        return repository.findAll().stream().map(RoaAlertConfiguration::toData).collect(Collectors.toList());
    }

    @Override
    public List<RoaAlertConfigurationData> findByFrequency(RoaAlertFrequency frequency) {
        return repository.findByFrequency(frequency).stream().map(RoaAlertConfiguration::toData).collect(Collectors.toList());
    }
}
