package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.rpki.ncc.core.domain.support.Entity;
import org.apache.commons.lang.Validate;

import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class InMemoryRepository<T extends Entity> implements Repository<T> {

    private final Set<T> entities = new HashSet<>();

    public abstract Class<T> getEntityClass();

    public void add(T entity) {
        entities.add(entity);
    }

    public T merge(T entity) {
         entities.add(entity);
         return entity;
    }

    public void addAll(Collection<? extends T> entities) {
        for (T entity : entities) {
            add(entity);
        }
    }

    public T find(Object id) {
        return find(getEntityClass(), id);
    }

    public <U extends T> U find(Class<U> type, Object id) {
        Validate.notNull(id, "id is null");
        for (T entity : entities) {
            if (type.isInstance(entity) && id.equals(entity.getId())) {
                return type.cast(entity);
            }
        }
        return null;
    }

    @Override
    public Collection<? extends T> findByIds(Collection<?> ids, LockModeType lockModeType) {
        return ids.stream().map(this::find).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public T get(Object id) throws EntityNotFoundException {
        return get(getEntityClass(), id);
    }

    public <U extends T> U get(Class<U> type, Object id) throws EntityNotFoundException {
        T result = find(type, id);
        if (result == null) {
            throw new EntityNotFoundException("entity '" + type.getSimpleName() + "' not found with id: " + id);
        }
        return type.cast(result);
    }

    public void remove(T entity) {
        boolean removed = entities.remove(entity);
        Validate.isTrue(removed, "Entity does not exist in repository: " + entity);
    }

    public boolean contains(T entity) {
        return entities.contains(entity);
    }

    public Set<T> findAll() {
        return new HashSet<>(entities);
    }

    public <U extends T> Set<U> findAll(Class<U> type) {
        Set<U> result = new HashSet<>();
        for (T entity: findAll()) {
            if (type.isInstance(entity)) {
                result.add(type.cast(entity));
            }
        }
        return result;
    }

    public int size() {
        return entities.size();
    }

}
