package net.ripe.rpki.ripencc.support.persistence;

import net.ripe.rpki.ncc.core.domain.support.Entity;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Collection;
import java.util.Set;


public abstract class JpaRepository<T extends Entity> implements Repository<T> {

    @PersistenceContext
    protected EntityManager manager;

    /**
     * @return The generic type of this Repository, used for class references.
     */
    protected abstract Class<T> getEntityClass();

    public void add(T entity) {
        validate(entity);
        manager.persist(entity);
    }

    public void addAll(Collection<? extends T> entities) {
        entities.forEach(this::add);
    }

    public boolean contains(T entity) {
        return manager.contains(entity);
    }

    public T find(Object id) {
        return find(getEntityClass(), id);
    }

    public <U extends T> U find(Class<U> type, Object id) {
        return manager.find(type, id);
    }

    public T get(Object id) throws EntityNotFoundException {
        return get(getEntityClass(), id);
    }

    public <U extends T> U get(Class<U> type, Object id) throws EntityNotFoundException {
        return manager.getReference(type, id);
    }

    @SuppressWarnings("unchecked")
    public Collection<T> findAll() {
        return manager.createQuery("from " + getEntityClass().getSimpleName()).getResultList();
    }

    @SuppressWarnings("unchecked")
    public Collection<? extends T> findByIds(Collection<?> ids, LockModeType lockModeType) {
        return manager.createQuery("from " + getEntityClass().getSimpleName() + " where id in :ids")
            .setParameter("ids", ids)
            .setLockMode(lockModeType)
            .getResultList();
    }

    public T merge(T entity) {
        validate(entity);
        return manager.merge(entity);
    }

    public void remove(T entity) {
        manager.remove(manager.merge(entity));
        manager.flush();
    }

    public void removeAll() {
        manager.createQuery("DELETE FROM " + getEntityClass().getSimpleName()).executeUpdate();
        manager.flush();
    }

    public int size() {
        Number count = (Number) manager.createQuery("select count(e) from " + getEntityClass().getSimpleName() + " e").getSingleResult();
        return count.intValue();
    }

    protected Query createQuery(String q) {
        return manager.createQuery(q);
    }

    protected Query createNativeQuery(String q) {
        return manager.createNativeQuery(q);
    }

    private void validate(T entity) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<T>> result = validator.validate(entity);
        if (!result.isEmpty()) {
            throw new IllegalStateException(result.toString());
        }
    }

    /**
     * Finds the first result from the query or null if no result was returned.
     */
    public static <T> T findFirstResult(TypedQuery<T> query) {
        return findUniqueResult(query.setMaxResults(1));
    }

    /**
     * Finds the first unique result from the query or null if no result was returned.
     */
    public static <T> T findUniqueResult(TypedQuery<T> query) {
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
