package net.ripe.rpki.domain.hsm;

import net.ripe.rpki.ripencc.support.persistence.Repository;

import java.util.Enumeration;
import java.util.Optional;

public interface HsmKeyStoreRepository extends Repository<HsmKeyStore>  {
    Optional<HsmKeyStore> findKeyStoreByName(String keyStoreName);

    Optional<HsmKey> findKeyByKeyStoreAndAlias(String keyStoreName, String alias);

    Enumeration<String> listKeyStores();

    Enumeration<String> listAliases(String keyStoreName);

    void deleteHsmKey(String keyStoreName, String alias);
}
