package net.ripe.rpki.server.api.dto;

/**
 * Key pair status.
 *
 * A more specific set of statuses that is aligned with <a href="https://www.rfc-editor.org/rfc/rfc6489.html#section-2">RFC6489 #2</a>
 */
public enum KeyPairStatus {
    PENDING, CURRENT, OLD
}
