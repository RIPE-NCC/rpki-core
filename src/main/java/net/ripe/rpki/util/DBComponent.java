package net.ripe.rpki.util;

public interface DBComponent {
    /**
     * Locks the certificate authority with the specified id.
     * @param caId the id of the certificate authority to lock
     * @return the id of the locked certificate authority's parent CA
     */
    Long lockCertificateAuthorityForUpdate(long caId);

    Long lockCertificateAuthorityForSharing(long caId);

    /**
     * Force version increment for the specified certificate authority
     * @param caId the id of the certificate authority to increment the version for
     */
    void lockCertificateAuthorityForceIncrement(long caId);

}
