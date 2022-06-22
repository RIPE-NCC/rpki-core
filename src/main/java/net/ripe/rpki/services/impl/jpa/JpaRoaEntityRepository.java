package net.ripe.rpki.services.impl.jpa;

import lombok.NonNull;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Component;

import javax.persistence.Query;
import java.util.List;

@Component
public class JpaRoaEntityRepository extends JpaRepository<RoaEntity> implements RoaEntityRepository {

    @Override
    protected Class<RoaEntity> getEntityClass() {
        return RoaEntity.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<RoaEntity> findByCertificateSigningKeyPair(@NonNull KeyPairEntity certificateSigningKeyPair) {
        Query query = createQuery("from RoaEntity re where re.certificate.signingKeyPair.id = :cskp");
        query.setParameter("cskp", certificateSigningKeyPair.getId());
        return query.getResultList();
    }

    @Override
    public int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair) {
        // use native sql to avoid stupid hibernate generating
        // 'org.postgresql.util.PSQLException: ERROR: syntax error at or near "cross"'
        final String sql = "DELETE FROM roaentity re " +
                "WHERE certificate_id IN (" +
                "  SELECT id FROM resourcecertificate " +
                "  WHERE signing_keypair_id = :keyPair" +
                ")";
        return manager.createNativeQuery(sql)
                .setParameter("keyPair", certificateSigningKeyPair.getId())
                .executeUpdate();
    }
}
