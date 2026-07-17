package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.ta.domain.response.ErrorResponse;
import net.ripe.rpki.commons.ta.domain.response.RevocationResponse;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;
import net.ripe.rpki.commons.ta.domain.response.TaResponse;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.List;

/**
 * Let the back-end handle an {@link net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse} response for the
 * {@link net.ripe.rpki.domain.AllResourcesCertificateAuthority all resources CA}.
 */
public class ProcessTrustAnchorResponseCommand extends CertificateAuthorityModificationCommand {

    private final TrustAnchorResponse response;

    public ProcessTrustAnchorResponseCommand(VersionedId certificateAuthorityId, TrustAnchorResponse response) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.response = response;
    }

    public TrustAnchorResponse getOfflineResponse() {
        return response;
    }

    @Override
    public String getCommandSummary() {
        List<TaResponse> taResponses = response.getTaResponses();
        if (taResponses.isEmpty()) {
            return "Process Trust Anchor response file with Republish Request Response containing TA objects.";
        } else {
            var sb = new StringBuilder("Process Trust Anchor response file with " + taResponses.size() + " response(s).");
            for (int i = 0; i < taResponses.size(); i++) {
                TaResponse taResponse = taResponses.get(i);
                sb.append("\nResponse #").append(i + 1).append(": ").append(getDetailsForResponse(taResponse));
            }
            return sb.toString();
        }
    }

    private String getDetailsForResponse(TaResponse taResponse) {
        if (taResponse instanceof SigningResponse signingResponse) {
            return treatAsNewCertificatesIssued(signingResponse);
        }
        if (taResponse instanceof RevocationResponse revocationResponse) {
            return treatAsKeyRevocation(revocationResponse);
        }
        if (taResponse instanceof ErrorResponse taErrorResponse) {
            return "Trust anchor failed to process this request. Reason: " + taErrorResponse.getMessage();
        }
        return "";
    }

    private String treatAsKeyRevocation(RevocationResponse response) {
        return "Revocation Notification for public key '" +
                response.getEncodedPublicKey() +
                "' for resource class '" +
                response.getResourceClassName() +
                "'.";
    }

    private String treatAsNewCertificatesIssued(SigningResponse response) {
        return "(Re-)Issue certificate at location " +
                response.getPublicationUri().toString();
    }
}
