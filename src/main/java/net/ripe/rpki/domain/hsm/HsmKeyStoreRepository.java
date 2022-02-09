package net.ripe.rpki.domain.hsm;

import net.ripe.rpki.ripencc.support.persistence.Repository;

import java.util.Enumeration;

public interface HsmKeyStoreRepository extends Repository<HsmKeyStore>  {
    HsmKeyStore findKeyStoreByName(String keyStoreName);

    HsmKey findKeyByKeyStoreAndAlias(String keyStoreName, String alias);

    Enumeration<String> listKeyStores();

    Enumeration<String> listAliases(String keyStoreName);

    void deleteHsmKey(String keyStoreName, String alias);
}
