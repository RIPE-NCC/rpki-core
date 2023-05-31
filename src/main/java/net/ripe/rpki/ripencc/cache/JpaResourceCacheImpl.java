package net.ripe.rpki.ripencc.cache;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.property.PropertyEntity;
import net.ripe.rpki.domain.property.PropertyEntityRepository;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.DelegationsCache;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
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
    public Optional<Instant> lastUpdateTime() {
        var entity = Optional.ofNullable(propertyEntityRepository.findByKey(RESOURCE_CACHE_UPDATE_KEY));
        return entity.map(PropertyEntity::getValue).flatMap(value -> {
            try {
                return Optional.of(Instant.parse(value));
            } catch (DateTimeParseException e) {
                log.info("Could not parse previously stored resource cache update time of {}", value);
                return Optional.empty();
            }
        });
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
    public Optional<ImmutableResourceSet> lookupResources(final CaName member) {
        var row = (Object[]) entityManager
            .createNativeQuery("SELECT EXISTS(SELECT 1 FROM resource_cache WHERE name = :productionCaName), (SELECT resources FROM resource_cache WHERE name = :member)")
            .setParameter("productionCaName", productionCaName.toString())
            .setParameter("member", member.toString())
            .getSingleResult();
        var available = (boolean) row[0];
        if (!available) {
            return Optional.empty();
        }
        String resources = Objects.requireNonNullElse((String) row[1], "");
        return Optional.of(ImmutableResourceSet.parse(resources));
    }

    @Override
    public Map<CaName, ImmutableResourceSet> allMemberResources() {
        return entityManager.createQuery(
            "SELECT rcl FROM ResourceCacheLine rcl where rcl.name != :prodCaName ",
            ResourceCacheLine.class)
            .setParameter("prodCaName", productionCaName.toString())
            .getResultList()
            .stream()
            .collect(Collectors.toMap(ResourceCacheLine::getName, ResourceCacheLine::getResources));
    }

    @Override
    public void populateCache(Map<CaName, ImmutableResourceSet> certifiableResources) {
        clearCache();
        // clear the session to avoid "duplicate entity in the session error from Hibernate"
        entityManager.clear();
        populateWith(certifiableResources);
        registerUpdateCompleted();
    }

    void clearCache() {
        entityManager.createQuery("delete from ResourceCacheLine rc where rc.name != :productionCAName")
                .setParameter("productionCAName", productionCaName.toString())
                .executeUpdate();
    }

    void dropCache() {
        entityManager.createQuery("delete from ResourceCacheLine rc")
                .executeUpdate();
    }

    private void populateWith(Map<CaName, ImmutableResourceSet> certifiableResources) {
        for (final Map.Entry<CaName, ImmutableResourceSet> entry : certifiableResources.entrySet()) {
            final ResourceCacheLine resourceCacheLine = new ResourceCacheLine(entry.getKey(), entry.getValue());
            entityManager.persist(resourceCacheLine);
        }
    }

    private void registerUpdateCompleted() {
        propertyEntityRepository.createOrUpdate(RESOURCE_CACHE_UPDATE_KEY, Instant.now().toString());
    }
    public void updateEntry(CaName caName, ImmutableResourceSet resources) {
        entityManager.createNativeQuery(
            "insert into resource_cache (name, resources) values (:name, :resources)\n" +
            "on conflict (name) do update set resources = EXCLUDED.resources")
            .setParameter("name", caName.toString())
            .setParameter("resources", resources.toString())
            .executeUpdate();
        ResourceCacheLine cacheRecord = entityManager.find(ResourceCacheLine.class, caName.toString());
        entityManager.refresh(cacheRecord);
    }

    @Override
    public void cacheDelegations(ImmutableResourceSet delegations) {
        entityManager.merge(new ResourceCacheLine(productionCaName, delegations));
    }

    @Override
    public Optional<ImmutableResourceSet> getDelegationsCache() {
        return lookupResources(productionCaName);
    }
}
