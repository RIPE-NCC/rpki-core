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

    private TrustAnchorResponse response;

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
            StringBuffer sb = new StringBuffer("Process Trust Anchor response file with " + taResponses.size() + " response(s).");
            for (int i =0; i < taResponses.size(); i++) {
                TaResponse taResponse = taResponses.get(i);
                sb.append("\nResponse #").append(i + 1).append(": ").append(getDetailsForResponse(taResponse));
            }
            return sb.toString();
        }
    }

    private String getDetailsForResponse(TaResponse taResponse) {
        String details = "";
        if (taResponse instanceof SigningResponse) {
            SigningResponse signingResponse = (SigningResponse) taResponse;
            details = treatAsNewCertificatesIssued(signingResponse);
        } else if (taResponse instanceof RevocationResponse) {
            details = treatAsKeyRevocation(((RevocationResponse) taResponse));
        } else if (taResponse instanceof ErrorResponse) {
            ErrorResponse taErrorResponse = (ErrorResponse) taResponse;
            details = "Trust anchor failed to process this request. Reason: " + taErrorResponse.getMessage();
        }
        return details;
    }

    private String treatAsKeyRevocation(RevocationResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Revocation Notification for public key '");
        sb.append(response.getEncodedPublicKey());
        sb.append("' for resource class '");
        sb.append(response.getResourceClassName());
        sb.append("'.");
        return sb.toString();
    }

    private String treatAsNewCertificatesIssued(SigningResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Re-)Issue certificate at location ");
        sb.append(response.getPublicationUri().toString());
        return sb.toString();
    }
}
