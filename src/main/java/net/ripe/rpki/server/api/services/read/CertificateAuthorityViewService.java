package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import org.joda.time.Instant;

import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only service for information related to CertificateAuthorities
 */
public interface CertificateAuthorityViewService {

    /**
     * Finds the certificate authority with the given name.
     *
     * @param name the name of the CA.
     * @return null if no such CA was found, the CA's data otherwise.
     */
    CertificateAuthorityData findCertificateAuthorityByName(X500Principal name);

    CertificateAuthorityData findCertificateAuthority(Long id);

    Long findCertificateAuthorityIdByName(X500Principal name);

    CertificateAuthorityData findCertificateAuthorityByTypeAndUuid(Class<? extends CertificateAuthority> type, UUID uuid);

    /**
     * Find CA id based on name and type
     */
    Long findCertificateAuthorityIdByTypeAndName(Class<? extends CertificateAuthority> type, X500Principal name);

    /**
     * @return non-null collection of hosted CA's with old keys
     */
    Collection<ManagedCertificateAuthorityData> findManagedCasEligibleForKeyRevocation();

    /**
     * @return non-null collection of hosted CA's that match the conditions to be included for key-roll.
     * The conditions mostly concern keypairs. CAs are selected when:
     *   * They have a CURRENT key that is old enough and no key with another status.
     *   * They do not have any keys
     *
     * @param batchSize |results|
     * @param oldestKpCreationTime oldest creation time of keypair to be selected
     * @param type type of ManagedCertificateAuthority to select
     */
    Collection<ManagedCertificateAuthorityData> findManagedCasEligibleForKeyRoll(
        Class<? extends ManagedCertificateAuthority> type,
        Instant oldestKpCreationTime,
        Optional<Integer> batchSize
    );

    /**
     * Use this to find all the child CAs of the (1) Production CA in the system.
     */
    Collection<CertificateAuthorityData> findAllChildrenForCa(X500Principal productionCaName);

    Collection<CaIdentity> findAllChildrenIdsForCa(X500Principal productionCaName);

        // Auditing, move to own interface?
    List<CommandAuditData> findMostRecentCommandsForCa(long caId);

    List<ProvisioningAuditData> findMostRecentMessagesForCa(UUID caUUID);

    Collection<CaStat> getCaStats();

    Collection<? extends CaStatEvent> getCaStatEvents();

    Map<UUID, RepositoryResponse> findNonHostedPublisherRepositories(X500Principal caName);

    Map<UUID, PublisherRequest> findAllPublisherRequestsFromNonHostedCAs();
    /**
     * @return all subclass instances of {@link ManagedCertificateAuthority ManagedCertificateAuthority}
     * that have a pending key, ordered by the depth of the parent CA chain (so the
     * {@link net.ripe.rpki.domain.AllResourcesCertificateAuthority AllResourcesCertificateAuthority} will be first,
     * followed by its immediate children, followed by their immediate children, etc).
     */
    List<CertificateAuthorityData> findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth();
}
