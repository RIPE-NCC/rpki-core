package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.NoResultException;
import javax.security.auth.x500.X500Principal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    public List<RoaConfigurationPerCa> findAllPerCa() {
        return (List<RoaConfigurationPerCa>) createNativeQuery("SELECT DISTINCT\n" +
                "    ca.id,\n" +
                "    ca.name,\n" +
                "    rcp.asn,\n" +
                "    rcp.prefix_type_id,\n" +
                "    rcp.prefix_start,\n" +
                "    rcp.prefix_end,\n" +
                "    rcp.maximum_length\n" +
                "FROM certificateauthority ca\n" +
                "JOIN roaconfiguration rc ON rc.certificateauthority_id = ca.id\n" +
                "JOIN roaconfiguration_prefixes rcp ON rcp.roaconfiguration_id = rc.id")
                .getResultList()
                .stream()
                .map(o -> {
                    final Object[] row = (Object[]) o;
                    final Long caId = ((Long) row[0]);
                    final X500Principal principal = new X500Principal((String) row[1]);
                    final CaName caName = CaName.of(principal);
                    final Asn asn = new Asn(((BigDecimal) row[2]).longValue());
                    final Short prefixType = (Short) row[3];
                    final BigInteger begin = ((BigDecimal) row[4]).toBigInteger();
                    final BigInteger end = ((BigDecimal) row[5]).toBigInteger();
                    final Integer maximumLength = (Integer) row[6];
                    final IpResourceType resourceType = IpResourceType.values()[prefixType];
                    final IpResourceRange range = resourceType.fromBigInteger(begin).upTo(resourceType.fromBigInteger(end));
                    return new RoaConfigurationPerCa(caId, caName, asn, range, maximumLength);
                }).toList();
    }

    @Override
    public void logRoaPrefixDeletion(RoaConfiguration configuration, Collection<? extends RoaConfigurationPrefix> deletedPrefixes) {
        // do it in SQL because Hibernate makes it harder to have the same enity
        String sql = "INSERT INTO deleted_roaconfiguration_prefixes " +
            "               (roaconfiguration_id, asn, prefix_type_id, prefix_start, prefix_end, maximum_length)" +
            "         VALUES (:roaconfiguration_id, :asn, :prefix_type_id, :prefix_start, :prefix_end, :maximum_length)";

        deletedPrefixes.forEach(dp -> {
            final IpRange prefix = dp.getPrefix();
            createNativeQuery(sql)
                .setParameter("roaconfiguration_id", configuration.getId())
                .setParameter("asn", dp.getAsn().longValue())
                .setParameter("prefix_type_id", prefix.getType() == IpResourceType.IPv4 ? 1 : 2)
                .setParameter("prefix_start", prefix.getStart().getValue())
                .setParameter("prefix_end", prefix.getEnd().getValue())
                .setParameter("maximum_length", dp.getMaximumLength())
                .executeUpdate();
        });
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
    protected Class<RoaConfiguration> getEntityClass() {
        return RoaConfiguration.class;
    }

}
