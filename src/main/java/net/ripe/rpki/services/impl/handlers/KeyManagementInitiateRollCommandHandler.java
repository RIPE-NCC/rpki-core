package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.ta.domain.request.ResourceCertificateRequestData;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.util.DBComponent;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;

@Handler
public class KeyManagementInitiateRollCommandHandler extends AbstractCertificateAuthorityCommandHandler<KeyManagementInitiateRollCommand> {

    private final KeyPairService keyPairService;

    private final CertificateRequestCreationService certificationRequestCreationService;

    private final ResourceCertificateRepository resourceCertificateRepository;
    private final DBComponent dbComponent;

    @Inject
    public KeyManagementInitiateRollCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                                   KeyPairService keyPairService,
                                                   CertificateRequestCreationService certificateRequestCreationService,
                                                   ResourceCertificateRepository resourceCertificateRepository,
                                                   DBComponent dbComponent) {
        super(certificateAuthorityRepository);
        this.keyPairService = keyPairService;
        this.certificationRequestCreationService = certificateRequestCreationService;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.dbComponent = dbComponent;
    }

    @Override
    public Class<KeyManagementInitiateRollCommand> commandType() {
        return KeyManagementInitiateRollCommand.class;
    }

    /**
     *  Step 1,2  of Key Rollover, see:  http://tools.ietf.org/html/rfc6489#section-2
     *
     * Instruct the CA to  initiate a key roll over for all its resource classes where:
     * - There is currently only one key in use, and key pair is in the state 'CURRENT'
     * - And that key is older than the key age threshold (days) specified.
     * - The CA will request certificate issuance for the new key with the *same resources* as the current key
     *
     */
    @Override
    public void handle(KeyManagementInitiateRollCommand command, CommandStatus commandStatus) {
        HostedCertificateAuthority ca = lookupHostedCA(command.getCertificateAuthorityId());
        List<CertificateIssuanceRequest> requests = ca.initiateKeyRolls(command.getMaxAgeDays(), keyPairService, certificationRequestCreationService);

        if (requests.isEmpty()) {
            throw new CommandWithoutEffectException(command);
        }

        if (ca.isAllResourcesCa()) {
            handleForAllResourcesCa((AllResourcesCertificateAuthority) ca, requests);
        } else {
            handleForHostedCertificateAuthority(ca, requests);
        }
    }

    private void handleForAllResourcesCa(AllResourcesCertificateAuthority ca, List<CertificateIssuanceRequest> requests) {
        ca.setUpStreamCARequestEntity(new UpStreamCARequestEntity(ca, certificationRequestCreationService.createTrustAnchorRequest(toTaRequests(requests))));
    }

    private void handleForHostedCertificateAuthority(HostedCertificateAuthority ca, List<CertificateIssuanceRequest> requests) {
        dbComponent.lockAndRefresh(ca.getParent());
        for (CertificateIssuanceRequest request : requests) {
            CertificateIssuanceResponse response = ca.getParent().processCertificateIssuanceRequest(
                    request, resourceCertificateRepository, dbComponent, Integer.MAX_VALUE);
            ca.processCertificateIssuanceResponse(response, resourceCertificateRepository);
        }
    }

    private List<TaRequest> toTaRequests(List<CertificateIssuanceRequest> requests) {
        return requests.stream()
                .map(request -> new SigningRequest(
                        ResourceCertificateRequestData.forUpstreamCARequest(
                                DEFAULT_RESOURCE_CLASS,
                                request.getSubjectDN(),
                                request.getSubjectPublicKey(),
                                request.getSubjectInformationAccess(),
                                request.getResources())))
                .collect(Collectors.toList());
    }

}
