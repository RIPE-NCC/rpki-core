package net.ripe.rpki.domain.inmemory;

import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.ripencc.support.persistence.InMemoryRepository;
import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;

import java.security.PublicKey;
import java.util.Collection;
import java.util.Optional;

public class InMemoryResourceCertificateRepository extends InMemoryRepository<ResourceCertificate> implements ResourceCertificateRepository {

    @Override
    public Class<ResourceCertificate> getEntityClass() {
        return ResourceCertificate.class;
    }

    @Override
    public OutgoingResourceCertificate findLatestOutgoingCertificate(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<OutgoingResourceCertificate> findAllBySigningKeyPair(KeyPairEntity signingKeyPairEntity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<IncomingResourceCertificate> findIncomingResourceCertificateBySubjectKeyPair(KeyPairEntity subjectKeyPair) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<OutgoingResourceCertificate> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<OutgoingResourceCertificate> findCurrentCertificatesBySubjectPublicKey(PublicKey subjectPublicKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteOutgoingCertificatesForRevokedKeyPair(KeyPairEntity signingKeyPair) {
        throw new NotImplementedException();
    }

    @Override
    public ExpireOutgoingResourceCertificatesResult expireOutgoingResourceCertificates(DateTime now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteExpiredOutgoingResourceCertificates(DateTime expirationTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAll() {
        throw new UnsupportedOperationException();
    }
}
