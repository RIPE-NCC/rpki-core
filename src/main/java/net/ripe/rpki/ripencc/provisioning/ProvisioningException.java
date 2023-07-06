package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;

import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;

/**
 * An error response as described at the end of https://datatracker.ietf.org/doc/html/rfc6492#section-3.2 .
 * These will <emph>not</emph> result in CMS signed responses.
 *
 * Interpretation: Used for situations where the request was never considered to be a current, valid, cms-signed request.
 * <emph>Recall:</emph> <i>@Transactional</i> rolls back on uncaught RuntimeExceptions, but not on checked exceptions.
 */
class ProvisioningException extends RuntimeException {
	private static final long serialVersionUID = 2L;

    private final ResponseExceptionType protocolError;

    private ProvisioningException(ResponseExceptionType protocolError) {
        super(format("%s: %s", protocolError.name(), protocolError.getDescription()));
        this.protocolError = protocolError;
    }

    private ProvisioningException(ResponseExceptionType protocolError, Throwable cause) {
        super(format("%s: %s", protocolError.name(), protocolError.getDescription()), cause);
        this.protocolError = protocolError;
    }

    public final String getName() {
        return protocolError.name();
    }

    public Optional<String> getSender() {
        return Optional.empty();
    }

    public int getHttpStatusCode() {
        return protocolError.getHttpResponseCode();
    }

    public String getDescription() {
        return protocolError.getDescription();
    }

    static class PotentialReplayAttack extends ProvisioningException {
        PotentialReplayAttack() {
            super(ResponseExceptionType.POTENTIAL_REPLAY_ATTACK);
        }
    }

    static class BadData extends ProvisioningException {
        BadData() {
            super(ResponseExceptionType.BAD_DATA);
        }

        BadData(Throwable cause) {
            super(ResponseExceptionType.BAD_DATA, cause);
        }
    }

    static class BadSenderAndRecipient extends ProvisioningException {
        private final String sender;

        BadSenderAndRecipient(String sender) {
            super(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT);
            this.sender = sender;
        }

        @Override
        public Optional<String> getSender() {
            return Optional.of(sender);
        }
    }

    static class UnknownRecipient extends ProvisioningException {
        UnknownRecipient() {
            super(ResponseExceptionType.UNKNOWN_RECIPIENT);
        }
    }

    static class UnknownSender extends ProvisioningException {
        private final UUID sender;

        UnknownSender(UUID sender) {
            super(ResponseExceptionType.UNKNOWN_SENDER);
            this.sender = sender;
        }

        @Override
        public Optional<String> getSender() {
            return Optional.of(sender.toString());
        }
    }

    static class UnknownProvisioningUrl extends ProvisioningException {
        UnknownProvisioningUrl() {
            super(ResponseExceptionType.UNKNOWN_PROVISIONING_URL);
        }
    }
}
