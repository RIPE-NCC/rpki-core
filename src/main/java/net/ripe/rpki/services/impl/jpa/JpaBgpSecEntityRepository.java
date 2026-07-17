package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.bgpsec.BgpSecEntity;
import net.ripe.rpki.domain.bgpsec.BgpSecEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaBgpSecEntityRepository extends JpaRepository<BgpSecEntity> implements BgpSecEntityRepository {

    @Override
    protected Class<BgpSecEntity> getEntityClass() {
        return BgpSecEntity.class;
    }

    @Override
    public List<BgpSecEntity> findCurrentByCertificateAuthority(ManagedCertificateAuthority certificateAuthority) {
        return manager.createQuery(
                        "SELECT bgpSec " +
                                "  FROM ManagedCertificateAuthority ca JOIN ca.keyPairs kp," +
                                "       BgpSecEntity bgpSec" +
                                " WHERE ca = :ca" +
                                "   AND bgpSec.certificate.signingKeyPair = kp",
                        BgpSecEntity.class
                )
                .setParameter("ca", certificateAuthority)
                .getResultList();
    }

    @Override
    public int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair) {
        return manager
                .createQuery("DELETE FROM BgpSecEntity " +
                        "WHERE certificate.id IN (" +
                        "   SELECT id FROM BgpSecCertificate bc " +
                        "   WHERE bc.signingKeyPair = :cskp)")
                .setParameter("cskp", certificateSigningKeyPair)
                .executeUpdate();
    }

}