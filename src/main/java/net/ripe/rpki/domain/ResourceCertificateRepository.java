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
     * Recursively query all the resources of all current outgoing resource certificates of this CA and all its children,
     * grandchildren, etc.
     *
     * <p>The recursion is only applied in case of a resource certificate that uses inherited resources (ASN, IPv4, and/or IPv6).</p>
     *
     * <p>This result is used to avoid removing resources from the incoming certificate that are still issued to child
     * CAs to avoid incorrectly invalidating child CA certificates due to an overclaiming resource set.</p>

     * @return find the union of the resources of _all_ current outgoing resource certificates of the CA with given name,
     * recursively down the CA tree.
     */
    ImmutableResourceSet findCurrentOutgoingChildCertificateResources(X500Principal caName);

    /**
     * @return find the union of the resources of _all_ current outgoing resource certificates
     * of the CA with given name.
     */
    ImmutableResourceSet findCurrentOutgoingResourceCertificateResources(X500Principal caName);

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
