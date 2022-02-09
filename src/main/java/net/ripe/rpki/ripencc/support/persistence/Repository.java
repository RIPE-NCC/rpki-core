package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.rpki.ncc.core.domain.support.Entity;

import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import java.util.Collection;

public interface Repository<T extends Entity> {

    void add(T entity);

    void addAll(Collection<? extends T> entities);

    T find(Object id);

    <U extends T> U find(Class<U> type, Object id);

    Collection<? extends T> findByIds(Collection<?> ids, LockModeType lockModeType);

    T get(Object id) throws EntityNotFoundException;

    <U extends T> U get(Class<U> type, Object id) throws EntityNotFoundException;

    void remove(T entity);

    boolean contains(T entity);

    Collection<T> findAll();

    void removeAll();

    int size();

    T merge(T entity);
}
