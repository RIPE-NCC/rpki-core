package net.ripe.rpki.server.api.commands;

/**
 * <p>
 * Indicates whether the command is typically user generated or system generated.
 * </p>
 * <p>
 * <b>NOTE: This will be removed in a future version. If you're interested in this look at the user that issued the command in the command audit...</b>
 * </p>
 */
public enum CertificateAuthorityCommandGroup {
	USER, SYSTEM
}
