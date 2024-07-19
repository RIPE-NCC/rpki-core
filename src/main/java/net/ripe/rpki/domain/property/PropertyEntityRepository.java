package net.ripe.rpki.domain.property;

import net.ripe.rpki.ripencc.support.persistence.Repository;

public interface PropertyEntityRepository extends Repository<PropertyEntity> {

    PropertyEntity findByKey(String key);

    void createOrUpdate(String key, String value);
}
