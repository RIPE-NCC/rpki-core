package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;

import java.util.List;
import java.util.Optional;

public interface BgpSecViewService {
    List<BgpSecConfigurationData> findBgpSecConfiguration(long caId);

    Optional<BgpSecConfigurationData> findBgpSecConfigurationById(long caId, long id);

    /**
     * Returns a DER-encoded PKCS#7 bundle containing only the BGPSec EE certificate for the given entry.
     */
    Optional<byte[]> findBgpSecCertificatePkcs7(long caId, BgpSecConfigurationData id);

    /**
     * Returns a DER-encoded PKCS#7 bundle containing the BGPSec EE certificate and issuer chain for the given entry.
     */
    Optional<byte[]> findBgpSecCertificateChainPkcs7(long caId, BgpSecConfigurationData id);
}
