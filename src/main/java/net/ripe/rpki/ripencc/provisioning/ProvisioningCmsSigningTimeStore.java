package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.ripencc.support.persistence.DateTimePersistenceConverter;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Manages the last seen provisioning CMS request signing time so that replay attacks can be prevented. The last
 * seen signing time must always be increasing.
 */
@Component
@Transactional
class ProvisioningCmsSigningTimeStore {
    private static final DateTimePersistenceConverter DATE_TIME_PERSISTENCE_CONVERTER = new DateTimePersistenceConverter();
    private final EntityManager entityManager;

    public ProvisioningCmsSigningTimeStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<DateTime> getLastSeenProvisioningCmsSignedAt(NonHostedCertificateAuthorityData nonHostedCertificateAuthority) {
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT last_seen_signed_at FROM provisioning_request_signing_time WHERE ca_id = :caId"
                )
                .setParameter("caId", nonHostedCertificateAuthority.getId())
                .getSingleResult();
            return Optional.of(DATE_TIME_PERSISTENCE_CONVERTER.convertToEntityAttribute((Timestamp) result));
        } catch (NoResultException notFound) {
            return Optional.empty();
        }
    }

    /**
     * Updates the last seen signing time if it is later than the currently stored time.
     * @return true if the signing time was updated, false if the provided signing time is earlier than the currently
     * stored signing time.
     */
    public boolean updateLastSeenProvisioningCmsSeenAt(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, DateTime cmsSigningTime) {
        int count = entityManager.createNativeQuery(
                "INSERT INTO provisioning_request_signing_time AS t (ca_id, last_seen_signed_at) " +
                    "     VALUES (:caId, :cmsSigningTime) " +
                    "ON CONFLICT (ca_id) DO UPDATE SET last_seen_signed_at = EXCLUDED.last_seen_signed_at " +
                    "      WHERE t.last_seen_signed_at < EXCLUDED.last_seen_signed_at"
            )
            .setParameter("caId", nonHostedCertificateAuthority.getId())
            .setParameter("cmsSigningTime", DATE_TIME_PERSISTENCE_CONVERTER.convertToDatabaseColumn(cmsSigningTime))
            .executeUpdate();
        return count > 0;
    }
}
