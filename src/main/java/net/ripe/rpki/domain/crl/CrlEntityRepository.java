package net.ripe.rpki.domain.crl;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.ripencc.support.persistence.Repository;

public interface CrlEntityRepository extends Repository<CrlEntity> {

    CrlEntity findByKeyPair(KeyPairEntity keyPair);
    CrlEntity findOrCreateByKeyPair(KeyPairEntity keyPair);

    int deleteByKeyPair(KeyPairEntity keyPair);
}
