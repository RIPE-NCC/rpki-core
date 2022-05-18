package net.ripe.rpki.domain.roa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.ripencc.support.persistence.Repository;

import java.util.List;

public interface RoaEntityRepository extends Repository<RoaEntity> {

    List<RoaEntity> findByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair);

    int deleteByCertificateSigningKeyPair(KeyPairEntity certificateSigningKeyPair);
}
