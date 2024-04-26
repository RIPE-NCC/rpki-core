package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.hsm.HsmKey;
import net.ripe.rpki.domain.hsm.HsmKeyStore;
import net.ripe.rpki.domain.hsm.HsmKeyStoreRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.TypedQuery;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

@Repository
@Transactional
public class JpaHsmKeyStoreRepository extends JpaRepository<HsmKeyStore> implements HsmKeyStoreRepository {
    @Override
    protected Class<HsmKeyStore> getEntityClass() {
        return HsmKeyStore.class;
    }

    @Override
    public Optional<HsmKeyStore> findKeyStoreByName(final String keyStoreName) {
        final TypedQuery<HsmKeyStore> q = manager
                .createQuery("FROM HsmKeyStore WHERE name = :keyStoreName", HsmKeyStore.class)
                .setParameter("keyStoreName", keyStoreName);
        return Optional.ofNullable(findUniqueResult(q));
    }

    @Override
    public Optional<HsmKey> findKeyByKeyStoreAndAlias(String keyStoreName, String alias) {
        final TypedQuery<HsmKey> q = manager.createQuery(
                "SELECT hk FROM HsmKeyStore hks " +
                        "JOIN hks.hsmKeys hk " +
                        "WHERE hks.name = :keyStoreName " +
                        "AND   hk.alias = :alias", HsmKey.class)
                .setParameter("keyStoreName", keyStoreName)
                .setParameter("alias", alias);
        return Optional.ofNullable(findUniqueResult(q));
    }

    @Override
    public Enumeration<String> listKeyStores() {
        TypedQuery<String> q = manager.createQuery("SELECT name FROM HsmKeyStore", String.class);
        return Collections.enumeration(q.getResultList());
    }

    @Override
    public Enumeration<String> listAliases(String keyStoreName) {
        final TypedQuery<String> q = manager.createQuery(
                "SELECT DISTINCT hk.alias " +
                        "FROM HsmKeyStore hks " +
                        "JOIN hks.hsmKeys hk " +
                        "WHERE hks.name = :keyStoreName ", String.class)
                .setParameter("keyStoreName", keyStoreName);
        return Collections.enumeration(q.getResultList());
    }

    @Override
    public void deleteHsmKey(String keyStoreName, String alias) {
        manager.createQuery(
                "DELETE FROM HsmKey hk " +
                        "WHERE hk.id IN (" +
                        "  SELECT id FROM HsmKey hk " +
                        "  WHERE hk.alias = :alias " +
                        "  AND   hk.hsmKeyStore.name = :keyStoreName " +
                        ")")
                .setParameter("alias", alias)
                .setParameter("keyStoreName", keyStoreName)
                .executeUpdate();
    }
}
