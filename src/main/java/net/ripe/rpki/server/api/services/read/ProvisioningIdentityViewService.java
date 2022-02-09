package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.commons.provisioning.identity.ParentIdentity;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;

import javax.security.auth.x500.X500Principal;

public interface ProvisioningIdentityViewService {

    /**
     * Return the parent identity material specific to a non-hosted CA
     */
    ParentIdentity getParentIdentityForNonHostedCa(X500Principal childName);

    /**
     * Get the provisioning details for this CA. This only applies to the
     * production certificate authority.
     */
    ProvisioningIdentityCertificate findProvisioningIdentityMaterial();

}
