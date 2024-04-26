package net.ripe.rpki.services.impl.jpa;

import com.google.common.base.Preconditions;
import net.ripe.rpki.domain.property.PropertyEntity;
import net.ripe.rpki.domain.property.PropertyEntityRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

@Component
public class JpaPropertyEntityRepository extends JpaRepository<PropertyEntity> implements PropertyEntityRepository {

    @Override
    protected Class<PropertyEntity> getEntityClass() {
        return PropertyEntity.class;
    }

    @Override
    @Nullable
    public PropertyEntity findByKey(String key) {
        Validate.notNull(key);
        Query q = createQuery("from PropertyEntity p where p.key = :key");
        q.setParameter("key", key);
        try {
            return (PropertyEntity) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public PropertyEntity getByKey(String key) {
        PropertyEntity entity = findByKey(key);
        Preconditions.checkNotNull(entity, "Could not find property by key: " + key);
        return entity;
    }

    @Override
    public void createOrUpdate(String key, String value) {
        PropertyEntity entity = findByKey(key);
        if (entity == null) {
            entity = new PropertyEntity(key, value);
            add(entity);
        } else {
            entity.setValue(value);
            merge(entity);
        }
    }
}
