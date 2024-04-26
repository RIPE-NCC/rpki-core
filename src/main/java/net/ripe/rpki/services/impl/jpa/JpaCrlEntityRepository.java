package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.springframework.stereotype.Component;

import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

@Component
public class JpaCrlEntityRepository extends JpaRepository<CrlEntity> implements CrlEntityRepository {

    @Override
    protected Class<CrlEntity> getEntityClass() {
        return CrlEntity.class;
    }

    @Override
    public CrlEntity findByKeyPair(KeyPairEntity keyPair) {
        Validate.notNull(keyPair);
        Query q = createQuery("FROM CrlEntity crl WHERE crl.keyPair.id = :keyPair");
        q.setParameter("keyPair", keyPair.getId());
        try {
            return (CrlEntity) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public CrlEntity findOrCreateByKeyPair(KeyPairEntity keyPair) {
        CrlEntity crl = findByKeyPair(keyPair);
        return crl == null ? new CrlEntity(keyPair) : crl;
    }

    @Override
    public int deleteByKeyPair(KeyPairEntity keyPair) {
        return manager.createQuery("DELETE FROM CrlEntity crl WHERE crl.keyPair.id = :keyPair")
                .setParameter("keyPair", keyPair.getId())
                .executeUpdate();
    }
}
