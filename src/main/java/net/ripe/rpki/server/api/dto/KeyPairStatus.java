package net.ripe.rpki.server.api.dto;

/**
 * Key pair status.
 *
 * A more specific set of statuses that is aligned with <a href="https://www.rfc-editor.org/rfc/rfc6489.html#section-2">RFC6489 #2</a>
 */
public enum KeyPairStatus {

    NEW,

    PENDING,

    CURRENT {
        @Override
        public boolean isRevokable() {
            return false;
        }
    },

    OLD,

    /**
     * A key signed by the off-line trust anchor cannot be immediately revoked,
     * so it gets the intermediate status MUSTREVOKE instead. As soon as the
     * response from the off-line trust anchor is processed the status will
     * change to REVOKED.
     */
    MUSTREVOKE,

    REVOKED {
        @Override
        public boolean isCertificateNeeded() {
            return false;
        }

        @Override
        public boolean isRevokable() {
            return false;
        }
    };

    public boolean isCertificateNeeded() {
        return true;
    }

    public boolean isRevokable() {
        return true;
    }
}
