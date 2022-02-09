package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.rpki.ripencc.support.persistence.Repository;
import org.joda.time.DateTime;

import java.security.PublicKey;
import java.util.Collection;
import java.util.Optional;

public interface ResourceCertificateRepository extends Repository<ResourceCertificate> {

    /**
     * Finds the latest (highest serial number) resource certificate used for
     * signing <code>subjectPublicKey</code> and signed by
     * <code>signingKeyPair</code>. Expired or revoked certificates are
     * ignored.
     *
     * @param subjectPublicKey
     * @param signingKeyPair
     * @return
     */
    OutgoingResourceCertificate findLatestOutgoingCertificate(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair);

    Collection<OutgoingResourceCertificate> findAllBySigningKeyPair(KeyPairEntity signingKeyPairEntity);

    /**
     * Finds all certificates signed with the specified
     * <code>signingKeyPair</code> that have been revoked but are not yet
     * expired.
     *
     * @param signingKeyPair
     * @param now
     * @return non-null collection of non-expired, revoked certificates signed
     *         by <code>signingKeyPair</code>.
     */
    Collection<OutgoingResourceCertificate> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now);

    Collection<OutgoingResourceCertificate> findCurrentCertificatesBySubjectPublicKey(PublicKey subjectPublicKey);

    boolean deleteOutgoingCertificatesForRevokedKeyPair(KeyPairEntity signingKeyPair);

    @Value
    class ExpireOutgoingResourceCertificatesResult {
        int expiredCertificateCount;
        int deletedRoaCount;
        int withdrawnObjectCount;
    }

    /**
     * Expires all outgoing resource certificates that have a <em>not valid after</em> time before <code>now</code>.
     *
     * Deletes ROA entities that were issued by a certificate that was expired. Withdraws all published objects for
     * the expired certificates or deleted ROAs.
     */
    ExpireOutgoingResourceCertificatesResult expireOutgoingResourceCertificates(DateTime now);

    int deleteExpiredOutgoingResourceCertificates(DateTime expirationTime);

    Optional<IncomingResourceCertificate> findIncomingResourceCertificateBySubjectKeyPair(KeyPairEntity subjectKeyPair);
}
