package net.ripe.rpki.services.impl;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.services.read.AspaViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class AspaServiceBean implements AspaViewService {

    private final CertificateAuthorityRepository caRepository;

    private final AspaConfigurationRepository aspaConfigurationRepository;

    public AspaServiceBean(CertificateAuthorityRepository caRepository,
                           AspaConfigurationRepository aspaConfigurationRepository) {
        this.caRepository = caRepository;
        this.aspaConfigurationRepository = aspaConfigurationRepository;
    }

    @Override
    public List<AspaConfigurationData> findAspaConfiguration(long caId) {
        ManagedCertificateAuthority ca = caRepository.findManagedCa(caId);
        if (ca == null) {
            return Collections.emptyList();
        }
        return aspaConfigurationRepository.findByCertificateAuthority(ca).values().stream()
            .map(AspaConfiguration::toData)
            .collect(Collectors.toList());
    }
}
