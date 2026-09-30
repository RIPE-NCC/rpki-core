package net.ripe.rpki.domain.bgpsec;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BgpSecConfigurationRepository {
    List<BgpSecConfiguration> findByCertificateAuthority(ManagedCertificateAuthority certificateAuthority);

    Optional<BgpSecConfiguration> findByCertificateAuthorityAndId(ManagedCertificateAuthority certificateAuthority, Long id);

    Optional<BgpSecConfigurationData> findCurrentConfigurationDataByCaId(long caId, long configurationId);    

    Collection<BgpSecConfiguration> findAll();

    void add(BgpSecConfiguration bgpSecConfiguration);

    void remove(BgpSecConfiguration bgpSecConfiguration);

    void removeById(ManagedCertificateAuthority ca, Long id);
}
