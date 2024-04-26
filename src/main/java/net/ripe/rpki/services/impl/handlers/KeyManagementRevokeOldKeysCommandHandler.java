package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.ta.domain.request.RevocationRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import jakarta.inject.Inject;
import java.util.List;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Handler
public class KeyManagementRevokeOldKeysCommandHandler implements CertificateAuthorityCommandHandler<KeyManagementRevokeOldKeysCommand> {

    private final CertificateAuthorityRepository caRepository;
    private final KeyPairDeletionService keyPairDeletionService;
    private final CertificateRequestCreationService certificateRequestCreationService;
    private final ResourceCertificateRepository resourceCertificateRepository;

    @Inject
    public KeyManagementRevokeOldKeysCommandHandler(CertificateAuthorityRepository caRepository,
                                                    KeyPairDeletionService keyPairDeletionService,
                                                    CertificateRequestCreationService certificateRequestCreationService,
                                                    ResourceCertificateRepository resourceCertificateRepository) {
        this.caRepository = caRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.resourceCertificateRepository = resourceCertificateRepository;
    }

    @Override
    public Class<KeyManagementRevokeOldKeysCommand> commandType() {
        return KeyManagementRevokeOldKeysCommand.class;
    }

    /**
     *  Step 6  of Key Rollover, see:  http://tools.ietf.org/html/rfc6489#section-2
     *  Handle revocation request for OLD CA (the CA Instance in the process of being removed)
     *  of which their keys  had been rolled over.
     */
    @Override
    public void handle(KeyManagementRevokeOldKeysCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority hostedCa = caRepository.findManagedCa(command.getCertificateAuthorityId());
        List<CertificateRevocationRequest> requests = hostedCa.requestOldKeysRevocation(resourceCertificateRepository);

        if (requests.isEmpty()) {
            throw new CommandWithoutEffectException(command);
        }

        if (hostedCa.isAllResourcesCa()) {
            ((AllResourcesCertificateAuthority) hostedCa).setUpStreamCARequestEntity(new UpStreamCARequestEntity(hostedCa,
                certificateRequestCreationService.createTrustAnchorRequest(toTaRequests(requests))));
        } else {
            requests.stream()
                .map(request -> hostedCa.getParent().processCertificateRevocationRequest(request, resourceCertificateRepository))
                .forEach(response -> hostedCa.processCertificateRevocationResponse(response, keyPairDeletionService));
        }
    }

    private List<TaRequest> toTaRequests(List<CertificateRevocationRequest> requests) {
        return requests.stream()
            .map(request -> (TaRequest) new RevocationRequest(DEFAULT_RESOURCE_CLASS, KeyPairUtil.getEncodedKeyIdentifier(request.getSubjectPublicKey())))
            .toList();
    }
}
