package net.ripe.rpki.server.api.security;


public final class CertificationRoles {

    private CertificationRoles() {
        //Utility classes should not have a public or default constructor.
    }

    public static final String ROLE_CA_OPERATOR = "certification";
    public static final String ROLE_MONITOR = "certification-monitor";

}
