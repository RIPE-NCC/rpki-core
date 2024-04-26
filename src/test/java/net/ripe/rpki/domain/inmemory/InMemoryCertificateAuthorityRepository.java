package net.ripe.rpki.domain.inmemory;

import jakarta.persistence.LockModeType;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.ripencc.support.persistence.InMemoryRepository;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import org.joda.time.DateTime;

import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemoryCertificateAuthorityRepository  extends InMemoryRepository<CertificateAuthority> implements CertificateAuthorityRepository {
    @Override
    public CertificateAuthority findByName(X500Principal name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndName(Class<T> type, X500Principal name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndUuid(Class<T> type, UUID uuid, LockModeType lockModeType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProductionCertificateAuthority findRootCAByName(X500Principal name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AllResourcesCertificateAuthority findAllResourcesCAByName(X500Principal name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<CertificateAuthority> findAllByParent(ParentCertificateAuthority parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagedCertificateAuthority findManagedCa(Long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NonHostedCertificateAuthority findNonHostedCa(Long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<CaStat> getCAStats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<CaStatEvent> getCAStatEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ManagedCertificateAuthority> findAllWithManifestsExpiringBefore(DateTime notValidAfterCutoff, int maxResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ManagedCertificateAuthority> findAllWithOutdatedManifests(boolean includeUpdatedConfiguration, DateTime nextUpdateCutoff, int maxResults) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteNonHostedPublicKeysWithoutSigningCertificates() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ManagedCertificateAuthority> getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<IntermediateCertificateAuthority> findSmallestIntermediateCA(X500Principal productionCaName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<CertificateAuthority> getEntityClass() {
        return CertificateAuthority.class;
    }

    @Override
    public void removeAll() {
        throw new UnsupportedOperationException();
    }
}
