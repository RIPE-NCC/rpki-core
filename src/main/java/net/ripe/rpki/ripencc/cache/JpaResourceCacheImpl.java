package net.ripe.rpki.ripencc.cache;

import com.google.common.base.Preconditions;
import lombok.Getter;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.property.PropertyEntity;
import net.ripe.rpki.domain.property.PropertyEntityRepository;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JpaResourceCacheImpl implements ResourceCache, DelegationsCache {

    private static final String RESOURCE_CACHE_UPDATE_KEY = "last_resource_cache_update";

    private final PropertyEntityRepository propertyEntityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Getter
    private final CaName productionCaName;

    @Autowired
    public JpaResourceCacheImpl(PropertyEntityRepository propertyEntityRepository,
                                RepositoryConfiguration configuration) {
        this.propertyEntityRepository = Preconditions.checkNotNull(propertyEntityRepository);
        this.productionCaName = CaName.of(configuration.getProductionCaPrincipal());
    }

    @Override
    public DateTime lastUpdateTime() {
        PropertyEntity entity = propertyEntityRepository.findByKey(RESOURCE_CACHE_UPDATE_KEY);
        return entity == null ? null : getDateFormatter().parseDateTime(entity.getValue());
    }

    @Override
    public void verifyResourcesArePresent() {
        if (hasNoProductionResources()) {
            throw new IllegalStateException("Resource cache doesn't contain production CA resources");
        }
        if (hasNoMemberResources()) {
            throw new IllegalStateException("Resource cache doesn't contain member CA resources");
        }
    }

    @Override
    public boolean hasNoMemberResources() {
        return isZeroCount("SELECT COUNT(*) FROM ResourceCacheLine rc where rc.name != :productionCAName");
    }

    @Override
    public boolean hasNoProductionResources() {
        return isZeroCount("SELECT COUNT(*) FROM ResourceCacheLine rc where rc.name = :productionCAName");
    }

    protected boolean isZeroCount(String query) {
        Number countMembers = (Number) entityManager.createQuery(query)
            .setParameter("productionCAName", productionCaName.toString())
            .getSingleResult();
        return countMembers.longValue() == 0;
    }

    @Override
    public Optional<IpResourceSet> lookupResources(final CaName user) {
        ResourceCacheLine cacheRecord = entityManager.find(ResourceCacheLine.class, user.toString());
        return Optional.ofNullable(cacheRecord).map(ResourceCacheLine::getResources);
    }

    @Override
    public Map<CaName, IpResourceSet> allMemberResources() {
        return entityManager.createQuery(
            "SELECT rcl FROM ResourceCacheLine rcl where rcl.name != :prodCaName ",
            ResourceCacheLine.class)
            .setParameter("prodCaName", productionCaName.toString())
            .getResultList()
            .stream()
            .collect(Collectors.toMap(ResourceCacheLine::getName, ResourceCacheLine::getResources));
    }

    @Override
    public void populateCache(Map<CaName, IpResourceSet> certifiableResources) {
        clearCache();
        populateWith(certifiableResources);
        registerUpdateCompleted();
    }

    void clearCache() {
        entityManager.createQuery("delete from ResourceCacheLine rc where rc.name != :productionCAName")
                .setParameter("productionCAName", productionCaName.toString())
                .executeUpdate();
    }

    public void dropCache() {
        entityManager.createQuery("delete from ResourceCacheLine rc")
                .executeUpdate();
    }

    private void populateWith(Map<CaName, IpResourceSet> certifiableResources) {
        for (final Map.Entry<CaName, IpResourceSet> entry : certifiableResources.entrySet()) {
            final ResourceCacheLine resourceCacheLine = new ResourceCacheLine(entry.getKey(), entry.getValue());
            entityManager.persist(resourceCacheLine);
        }
    }

    private void registerUpdateCompleted() {
        String dateTimeString = getDateFormatter().print(new DateTime());
        propertyEntityRepository.createOrUpdate(RESOURCE_CACHE_UPDATE_KEY, dateTimeString);
    }

    private DateTimeFormatter getDateFormatter() {
        return ISODateTimeFormat.dateTime();
    }

    @Override
    public void updateEntry(CaName caName, IpResourceSet resources) {
        entityManager.createNativeQuery(
            "insert into resource_cache (name, resources) values (:name, :resources)\n" +
            "on conflict (name) do update set resources = EXCLUDED.resources")
            .setParameter("name", caName.toString())
            .setParameter("resources", resources.toString())
            .executeUpdate();
        ResourceCacheLine cacheRecord = entityManager.find(ResourceCacheLine.class, caName.toString());
        entityManager.refresh(cacheRecord);
    }

    /**
     * for unit testing only
     */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void cacheDelegations(IpResourceSet delegations) {
        entityManager.merge(new ResourceCacheLine(productionCaName, delegations));
    }

    @Override
    public Optional<IpResourceSet> getDelegationsCache() {
        return lookupResources(productionCaName);
    }
}
