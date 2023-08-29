package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.ta.domain.request.ResourceCertificateRequestData;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;

import javax.inject.Inject;
import java.util.Collections;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Slf4j
@Handler
public class KeyManagementInitiateRollCommandHandler extends AbstractCertificateAuthorityCommandHandler<KeyManagementInitiateRollCommand> {

    private final CertificateRequestCreationService certificationRequestCreationService;

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final ManifestPublicationService manifestPublicationService;

    @Inject
    public KeyManagementInitiateRollCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                   CertificateRequestCreationService certificateRequestCreationService,
                                                   ResourceCertificateRepository resourceCertificateRepository,
                                                   ManifestPublicationService manifestPublicationService) {
        super(certificateAuthorityRepository);
        this.certificationRequestCreationService = certificateRequestCreationService;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.manifestPublicationService = manifestPublicationService;
    }

    @Override
    public Class<KeyManagementInitiateRollCommand> commandType() {
        return KeyManagementInitiateRollCommand.class;
    }

    /**
     *  Step 1,2  of Key Rollover, see:  <a href="http://tools.ietf.org/html/rfc6489#section-2">RFC6489 section 2</a>.
     *
     * <p>
     * Instruct the CA to  initiate a key roll over for all its resource classes where:
     * - There is currently only one key in use, and key pair is in the state 'CURRENT'
     * - And that key is older than the key age threshold (days) specified.
     * - The CA will request certificate issuance for the new key with the *same resources* as the current key
     * - The CA is eligible for a certificate: has current certified resources (for minimal change), or CA has resources
     *   according to DB
     * </p>
     */
    @Override
    public void handle(KeyManagementInitiateRollCommand command, CommandStatus commandStatus) {
        ManagedCertificateAuthority ca = lookupManagedCa(command.getCertificateAuthorityId());

        var optionalRequest = certificationRequestCreationService.initiateKeyRoll(ca, command.getMaxAgeDays());
        if (optionalRequest.isEmpty()) {
            throw new CommandWithoutEffectException(command);
        }

        CertificateIssuanceRequest request = optionalRequest.get();
        if (ca.isAllResourcesCa()) {
            handleForAllResourcesCa((AllResourcesCertificateAuthority) ca, request);
        } else {
            handleForManagedCertificateAuthority(ca, request);
            // Publish manifest and CRL immediately for new key so that they are available by the time the parent
            // CA publishes the referencing resource certificate. This avoids dangling SIA references in the repository.
            var keyPair = ca.findKeyPairByPublicKey(request.getSubjectPublicKey())
                .orElseThrow(() -> new IllegalStateException("new key pair not present on CA"));
            manifestPublicationService.publishRpkiObjectsIfNeeded(keyPair);
        }
    }

    private void handleForAllResourcesCa(AllResourcesCertificateAuthority ca, CertificateIssuanceRequest request) {
        ca.setUpStreamCARequestEntity(new UpStreamCARequestEntity(ca, certificationRequestCreationService.createTrustAnchorRequest(
            Collections.singletonList(toTaRequests(request))
        )));
    }

    private void handleForManagedCertificateAuthority(ManagedCertificateAuthority ca, CertificateIssuanceRequest request) {
        CertificateIssuanceResponse response = ca.getParent().processCertificateIssuanceRequest(
            ca, request, resourceCertificateRepository, Integer.MAX_VALUE);
        ca.processCertificateIssuanceResponse(response, resourceCertificateRepository);
    }

    private TaRequest toTaRequests(CertificateIssuanceRequest request) {
        return new SigningRequest(
            ResourceCertificateRequestData.forUpstreamCARequest(
                DEFAULT_RESOURCE_CLASS,
                request.getSubjectDN(),
                request.getSubjectPublicKey(),
                request.getSubjectInformationAccess(),
                new IpResourceSet(request.getResourceExtension().getResources())));
    }

}
