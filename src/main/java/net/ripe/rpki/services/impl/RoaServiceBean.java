package net.ripe.rpki.services.impl;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaEntityData;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.NoResultException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class RoaServiceBean implements RoaViewService {

    private CertificateAuthorityRepository caRepository;

    private RoaConfigurationRepository roaConfigurationRepository;

    private RoaEntityRepository roaEntityRepository;


    public RoaServiceBean(CertificateAuthorityRepository caRepository,
                          RoaConfigurationRepository roaConfigurationRepository,
                          RoaEntityRepository roaEntityRepository) {
        this.caRepository = caRepository;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.roaEntityRepository = roaEntityRepository;
    }

    private Collection<RoaEntity> findAllRoas(HostedCertificateAuthority ca) {
        return ca.getKeyPairs()
            .stream()
            .flatMap(kp -> roaEntityRepository.findByCertificateSigningKeyPair(kp).stream())
            .collect(Collectors.toList());
    }

    @Override
    public List<RoaEntityData> findAllRoasForCa(Long caId) {
        HostedCertificateAuthority ca = caRepository.findHostedCa(caId);
        return findAllRoas(ca).stream().map(this::convertToRoaEntityData).collect(Collectors.toList());
    }

    @Override
    public RoaConfigurationData getRoaConfiguration(long caId) {
        HostedCertificateAuthority certificateAuthority = caRepository.findHostedCa(caId);
        if (certificateAuthority == null) {
            throw new NoResultException();
        }
        return roaConfigurationRepository.findByCertificateAuthority(certificateAuthority)
            .orElseGet(() -> new RoaConfiguration(certificateAuthority))
            .convertToData();
    }

    private RoaEntityData convertToRoaEntityData(RoaEntity roaEntity) {
        return roaEntity == null ? null : roaEntity.toData();
    }
}
