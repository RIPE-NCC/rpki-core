package net.ripe.rpki.domain;

import net.ripe.rpki.ripencc.support.persistence.Repository;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CaStat;
import org.joda.time.DateTime;

import jakarta.persistence.LockModeType;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateAuthorityRepository extends Repository<CertificateAuthority> {

    CertificateAuthority findByName(X500Principal name);

    <T extends CertificateAuthority> T findByTypeAndName(Class<T> type, X500Principal name);

    <T extends CertificateAuthority> T findByTypeAndUuid(Class<T> type, UUID uuid, LockModeType lockModeType);

    ProductionCertificateAuthority findRootCAByName(X500Principal name);

    AllResourcesCertificateAuthority findAllResourcesCAByName(X500Principal name);

    Collection<CertificateAuthority> findAllByParent(ParentCertificateAuthority parent);

    ManagedCertificateAuthority findManagedCa(Long id);

    NonHostedCertificateAuthority findNonHostedCa(Long id);

    Collection<CaStat> getCAStats();

    Collection<CaStatEvent> getCAStatEvents();

    List<ManagedCertificateAuthority> findAllWithManifestsExpiringBefore(DateTime notValidAfterCutoff, int maxResult);

    Collection<ManagedCertificateAuthority> findAllWithOutdatedManifests(boolean includeUpdatedConfiguration, DateTime nextUpdateCutoff, int maxResults);

    int deleteNonHostedPublicKeysWithoutSigningCertificates();

    Collection<ManagedCertificateAuthority> getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear();

    /**
     * Find the smallest intermediate CA below the CA specified by <code>productionCaName</code>. The smallest CA is the CA with the fewest
     * children. In case there are multiple CAs with the same number of children a random one is picked.
     * @param productionCaName the name of the parent production CA.
     * @return the smallest CA or empty when there are no intermediate CAs under the production CA.
     */
    Optional<IntermediateCertificateAuthority> findSmallestIntermediateCA(X500Principal productionCaName);
}
