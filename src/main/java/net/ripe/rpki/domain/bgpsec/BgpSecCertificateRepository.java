package net.ripe.rpki.domain.bgpsec;

import lombok.Value;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.RevokedCertificateEntry;
import net.ripe.rpki.ripencc.support.persistence.Repository;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.Optional;

public interface BgpSecCertificateRepository extends Repository<BgpSecCertificate> {

    Collection<RevokedCertificateEntry> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now);

    ExpireBgpSecCertificatesResult expireOutdatedBgpSecCertificates(DateTime now);

    int deleteExpiredBgpSecCertificates(DateTime expirationTime);

    Optional<BgpSecConfigurationData> findCurrentCertificateDataByCaId(long caId, long configurationId);

    @Value
    class ExpireBgpSecCertificatesResult {
        long expiredCertificateCount;
        long deletedBgpSecCount;
        long withdrawnObjectCount;
    }
}

