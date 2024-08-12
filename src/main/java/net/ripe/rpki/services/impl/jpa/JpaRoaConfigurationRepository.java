package net.ripe.rpki.services.impl.jpa;

import jakarta.persistence.Query;
import net.ripe.ipresource.*;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.NoResultException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
@Transactional
public class JpaRoaConfigurationRepository extends JpaRepository<RoaConfiguration> implements RoaConfigurationRepository {

    @Override
    public Optional<RoaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority certificateAuthority) {
        try {
            return Optional.of((RoaConfiguration) createQuery("select rc from RoaConfiguration rc where rc.certificateAuthority.id = :caId")
                .setParameter("caId", certificateAuthority.getId())
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public RoaConfiguration getOrCreateByCertificateAuthority(ManagedCertificateAuthority certificateAuthority) {
        return findByCertificateAuthority(certificateAuthority).orElseGet(() -> {
            RoaConfiguration result = new RoaConfiguration(certificateAuthority);
            add(result);
            return result;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<RoaConfigurationPrefixData> findAllPrefixes() {
        return createNativeQuery(
                "SELECT DISTINCT asn, prefix_type_id, prefix_start, prefix_end, maximum_length FROM roaconfiguration_prefixes")
                .getResultList()
                .stream()
                .map(o -> {
                    final Object[] row = (Object[]) o;
                    final Asn asn = new Asn(((BigDecimal) row[0]).longValue());
                    final Short prefixType = (Short) row[1];
                    final BigInteger begin = ((BigDecimal) row[2]).toBigInteger();
                    final BigInteger end = ((BigDecimal) row[3]).toBigInteger();
                    final Integer maximumLength = (Integer) row[4];
                    final IpResourceType resourceType = IpResourceType.values()[prefixType];
                    final IpRange range = IpRange.range(
                            (IpAddress)resourceType.fromBigInteger(begin),
                            (IpAddress)resourceType.fromBigInteger(end));
                    return new RoaConfigurationPrefixData(asn, range, maximumLength);
                }).toList();
    }

    @Override
    public int countRoaPrefixes() {
        String sql = "SELECT count(*) from roaconfiguration_prefixes";
        return ((Long)createNativeQuery(sql).getSingleResult()).intValue();
    }

    @Override
    public Optional<Instant> lastModified() {
        String sql = "SELECT max(last) from (" +
                "SELECT max(updated_at) as last from roaconfiguration " +
                "UNION " +
                "SELECT max(deleted_at) as last from deleted_roaconfiguration_prefixes" +
                ") last_changes";
        // empty table -> null.
        var res = (Instant) createNativeQuery(sql).getSingleResult();
        return Optional.ofNullable(res);
    }

    @Override
    public void mergePrefixes(RoaConfiguration configuration,
                              Collection<RoaConfigurationPrefix> prefixesToAdd,
                              Collection<RoaConfigurationPrefix> prefixesToRemove) {
        var diff = configuration.mergePrefixes(prefixesToAdd, prefixesToRemove);
        applyDiff(configuration, diff);
    }

    public void applyDiff(RoaConfiguration configuration,
                          RoaConfiguration.PrefixDiff diff) {

        diff.removed().forEach(r -> {
            String sql = """
                    WITH deleted AS (
                        DELETE FROM roaconfiguration_prefixes
                        WHERE roaconfiguration_id = :roaconfiguration_id
                        AND asn = :asn
                        AND prefix_type_id = :prefix_type_id
                        AND prefix_start = :prefix_start
                        AND prefix_end = :prefix_end
                        AND maximum_length = :maximum_length
                        RETURNING *
                    )
                    INSERT INTO deleted_roaconfiguration_prefixes
                    SELECT * FROM deleted
                    """;
            executeForPrefix(configuration, r, sql);
        });

        diff.added().forEach(r -> {
            String sql = """
                    INSERT INTO roaconfiguration_prefixes (roaconfiguration_id, asn, prefix_type_id, prefix_start, prefix_end, maximum_length)
                    VALUES (:roaconfiguration_id, :asn, :prefix_type_id, :prefix_start, :prefix_end, :maximum_length)
                    RETURNING updated_at
                    """;
            Instant updatedAt = extractForPrefix(configuration, r, sql);
            r.setUpdatedAt(updatedAt);
        });
    }

    private void executeForPrefix(RoaConfiguration configuration, RoaConfigurationPrefix dp, String sql) {
        final IpRange prefix = dp.getPrefix();
        makeQuery(configuration, dp, sql, prefix).executeUpdate();
    }

    private Instant extractForPrefix(RoaConfiguration configuration, RoaConfigurationPrefix dp, String sql) {
        final IpRange prefix = dp.getPrefix();
        return (Instant) makeQuery(configuration, dp, sql, prefix).getSingleResult();
    }

    private Query makeQuery(RoaConfiguration configuration, RoaConfigurationPrefix dp, String sql, IpRange prefix) {
        return createNativeQuery(sql)
                .setParameter("roaconfiguration_id", configuration.getId())
                .setParameter("asn", dp.getAsn().longValue())
                .setParameter("prefix_type_id", prefix.getType() == IpResourceType.IPv4 ? 1 : 2)
                .setParameter("prefix_start", prefix.getStart().getValue())
                .setParameter("prefix_end", prefix.getEnd().getValue())
                .setParameter("maximum_length", dp.getMaximumLength());
    }

    @Override
    protected Class<RoaConfiguration> getEntityClass() {
        return RoaConfiguration.class;
    }

}
