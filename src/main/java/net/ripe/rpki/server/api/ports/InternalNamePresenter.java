package net.ripe.rpki.server.api.ports;

import javax.security.auth.x500.X500Principal;

public interface InternalNamePresenter {
    /**
     * Turns the name of a CA into a human readable string for the stats collector report.
     */
    String humanizeCaName(X500Principal caName);

    /**
     * Turns an internal user principal into a human readable string for the stats collector report.
     */
    String humanizeUserPrincipal(String commandPrincipal);
}
