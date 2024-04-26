package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages the last seen provisioning CMS request signing time so that replay attacks can be prevented. The last
 * seen signing time must always be increasing.
 */
@Component
@Transactional
class ProvisioningCmsSigningTimeStore {
    private final EntityManager entityManager;

    public ProvisioningCmsSigningTimeStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<Instant> getLastSeenProvisioningCmsSignedAt(NonHostedCertificateAuthorityData nonHostedCertificateAuthority) {
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT last_seen_signed_at FROM provisioning_request_signing_time WHERE ca_id = :caId"
                )
                .setParameter("caId", nonHostedCertificateAuthority.getId())
                .getSingleResult();
            return Optional.of((Instant) result);
        } catch (NoResultException notFound) {
            return Optional.empty();
        }
    }

    /**
     * Updates the last seen signing time if it is later than the currently stored time.
     * @return true if the signing time was updated, false if the provided signing time is earlier than the currently
     * stored signing time.
     */
    public boolean updateLastSeenProvisioningCmsSeenAt(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, Instant cmsSigningTime) {
        int count = entityManager.createNativeQuery(
                "INSERT INTO provisioning_request_signing_time AS t (ca_id, last_seen_signed_at) " +
                    "     VALUES (:caId, :cmsSigningTime) " +
                    "ON CONFLICT (ca_id) DO UPDATE SET last_seen_signed_at = EXCLUDED.last_seen_signed_at " +
                    "      WHERE t.last_seen_signed_at < EXCLUDED.last_seen_signed_at"
            )
            .setParameter("caId", nonHostedCertificateAuthority.getId())
            .setParameter("cmsSigningTime", cmsSigningTime)
            .executeUpdate();
        return count > 0;
    }

    /**
     * Wrap for joda-time DateTime
     */
    public boolean updateLastSeenProvisioningCmsSeenAt(NonHostedCertificateAuthorityData nonHostedCertificateAuthority, DateTime cmsSigningJodaTime) {
        return updateLastSeenProvisioningCmsSeenAt(nonHostedCertificateAuthority, Instant.ofEpochMilli(cmsSigningJodaTime.getMillis()));
    }
}
