package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import net.ripe.rpki.domain.bgpsec.BgpSecConfigurationRepository;
import net.ripe.rpki.domain.bgpsec.RouterId;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.joda.time.DateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaBgpSecConfigurationRepository extends JpaRepository<BgpSecConfiguration> implements BgpSecConfigurationRepository {

    private record BgpSecCertificateRow(
            BgpSecConfiguration conf,
            DateTime notValidBefore,
            DateTime notValidAfter
    ) {}

    @Override
    public Optional<BgpSecConfigurationData> findCurrentConfigurationDataByCaId(long caId, long configurationId) {
        var jpql = """
                SELECT new net.ripe.rpki.services.impl.jpa.JpaBgpSecConfigurationRepository$BgpSecCertificateRow(
                       conf,
                       c.validityPeriod.notValidBefore, c.validityPeriod.notValidAfter)
                FROM BgpSecConfiguration conf
                LEFT JOIN BgpSecEntity e
                    ON e.asn = conf.asn
                    AND (e.routerId = conf.routerId OR (e.routerId IS NULL AND conf.routerId IS NULL))
                    AND e.keyIdentifier = conf.keyIdentifier
                LEFT JOIN e.certificate c
                    ON c.status = :status
                    AND c.signingKeyPair MEMBER OF conf.certificateAuthority.keyPairs
                WHERE conf.certificateAuthority.id = :caId
                AND conf.id = :configurationId
                ORDER BY c.validityPeriod.notValidAfter DESC NULLS LAST
                """;

        var query = manager.createQuery(jpql, BgpSecCertificateRow.class)
                .setParameter("caId", caId)
                .setParameter("status", CertificateStatus.CURRENT)
                .setParameter("configurationId", configurationId);

        return query.getResultList().stream()
                .map(row -> new BgpSecConfigurationData(
                        row.conf().getId(),
                        row.conf().getAsn(),
                        row.conf().getRouterId() != null ? new RouterId(row.conf().getRouterId()) : null,
                        row.conf().getCsr(),
                        row.conf().getKeyIdentifier(),
                        row.notValidBefore(),
                        row.notValidAfter()
                ))
                .findFirst();
    }

    @Override
    public List<BgpSecConfiguration> findByCertificateAuthority(ManagedCertificateAuthority ca) {
        return manager
                .createQuery("from BgpSecConfiguration where certificateAuthority.id = :caId order by asn", BgpSecConfiguration.class)
                .setParameter("caId", ca.getId())
                .getResultStream()
                .toList();
    }

    @Override
    public Optional<BgpSecConfiguration> findByCertificateAuthorityAndId(ManagedCertificateAuthority ca, Long id) {
        return manager
            .createQuery("FROM BgpSecConfiguration WHERE certificateAuthority = :ca AND id = :id", BgpSecConfiguration.class)
            .setParameter("ca", ca)
            .setParameter("id", id)
            .getResultStream()
            .findAny();
    }

    @Override
    public void removeById(ManagedCertificateAuthority ca, Long id) {
        manager.createQuery("""
                DELETE FROM BgpSecConfiguration
                WHERE certificateAuthority = :ca
                  AND id = :id
                """)
            .setParameter("ca", ca)
            .setParameter("id", id)
            .executeUpdate();
    }

    @Override
    protected Class<BgpSecConfiguration> getEntityClass() {
        return BgpSecConfiguration.class;
    }
}
