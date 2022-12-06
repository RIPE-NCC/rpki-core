package net.ripe.rpki.domain;

import lombok.Value;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.ripencc.support.persistence.Repository;
import org.joda.time.DateTime;

import javax.security.auth.x500.X500Principal;
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

    /**
     * Counts the number of outgoing resource certificates for the specific <code>subjectPublicKey</code>,
     * and signed by <code>signingKeyPair</code>. This count is used to limit the number of certificates
     * for a specific public key to avoid having too many and causing CRL bloat, this is why all non-expired
     * certificates are included in the count.
     */
    int countNonExpiredOutgoingCertificates(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair);

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

    boolean existsCurrentOutgoingCertificatesExceptForManifest(KeyPairEntity signingKeyPair);

    /**
     * @return find the union of the resources of _all_ current child certificates of the CA with given name.
     */
    ImmutableResourceSet findCurrentOutgoingChildCertificateResources(X500Principal caName);

    /**
     * @return find the union of the resources of _all_ current outgoing RPKI object (ROA, Manifest, etc.) certificates
     * of the CA with given name.
     */
    ImmutableResourceSet findCurrentOutgoingRpkiObjectCertificateResources(X500Principal caName);

    @Value
    class ExpireOutgoingResourceCertificatesResult {
        int expiredCertificateCount;
        int deletedRoaCount;
        int deletedAspaCount;
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
