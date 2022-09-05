package net.ripe.rpki.domain;

import net.ripe.rpki.ripencc.support.persistence.Repository;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CaStat;
import org.joda.time.DateTime;

import javax.persistence.LockModeType;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CertificateAuthorityRepository extends Repository<CertificateAuthority> {

    CertificateAuthority findByName(X500Principal name);

    <T extends CertificateAuthority> T findByTypeAndName(Class<T> type, X500Principal name);

    <T extends CertificateAuthority> T findByTypeAndUuid(Class<T> type, UUID memberUuid, LockModeType lockModeType);

    ProductionCertificateAuthority findRootCAByName(X500Principal name);

    AllResourcesCertificateAuthority findAllresourcesCAByName(X500Principal name);

    Collection<CertificateAuthority> findAllByParent(ParentCertificateAuthority parent);

    ManagedCertificateAuthority findManagedCa(Long id);

    NonHostedCertificateAuthority findNonHostedCa(Long id);

    Collection<CaStat> getCAStats();

    Collection<CaStatEvent> getCAStatEvents();

    List<ManagedCertificateAuthority> findAllWithManifestsExpiringBefore(DateTime notValidAfterCutoff, int maxResult);

    Collection<ManagedCertificateAuthority> findAllWithOutdatedManifests(DateTime nextUpdateCutoff, int maxResults);

    int deleteNonHostedPublicKeysWithoutSigningCertificates();

    Collection<ManagedCertificateAuthority> getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear();
}
