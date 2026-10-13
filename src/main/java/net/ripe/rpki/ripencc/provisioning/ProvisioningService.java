package net.ripe.rpki.ripencc.provisioning;

/**
 * See: http://tools.ietf.org/html/draft-ietf-sidr-rescerts-provisioning-11
 */
public interface ProvisioningService {
    byte[] processRequest(byte[] request, String userAgent) throws ProvisioningException;
}
