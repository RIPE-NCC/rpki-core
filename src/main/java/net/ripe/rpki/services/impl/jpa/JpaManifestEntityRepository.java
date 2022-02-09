package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.springframework.stereotype.Repository;

import javax.persistence.NoResultException;
import javax.persistence.Query;

@Repository
public class JpaManifestEntityRepository extends JpaRepository<ManifestEntity> implements ManifestEntityRepository {

    @Override
    protected Class<ManifestEntity> getEntityClass() {
        return ManifestEntity.class;
    }

    @Override
    public ManifestEntity findByKeyPairEntity(KeyPairEntity keyPair) {
        Validate.notNull(keyPair);
        Query q = createQuery("from ManifestEntity m where m.keyPair.id = :keyPair");
        q.setParameter("keyPair", keyPair.getId());
        try {
            return (ManifestEntity) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public ManifestEntity findOrCreateByKeyPairEntity(KeyPairEntity keyPair) {
        ManifestEntity manifest = findByKeyPairEntity(keyPair);
        return manifest == null ? new ManifestEntity(keyPair) : manifest;
    }

    @Override
    public int deleteByKeyPairEntity(KeyPairEntity keyPair) {
        return manager.createQuery("DELETE FROM ManifestEntity me WHERE me.keyPair.id = :keyPair")
                .setParameter("keyPair", keyPair.getId())
                .executeUpdate();
    }
}
