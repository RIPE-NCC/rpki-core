package net.ripe.rpki.domain.manifest;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.ripencc.support.persistence.Repository;

public interface ManifestEntityRepository extends Repository<ManifestEntity> {

    ManifestEntity findByKeyPairEntity(KeyPairEntity keyPair);
    ManifestEntity findOrCreateByKeyPairEntity(KeyPairEntity keyPair);

    int deleteByKeyPairEntity(KeyPairEntity keyPair);

}
