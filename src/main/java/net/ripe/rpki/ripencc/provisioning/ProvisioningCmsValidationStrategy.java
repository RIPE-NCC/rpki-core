package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;

/**
 * Extract the validation strategy.
 *
 * Needed to enable the tests to use mock objects for the high level behaviour instead of always requiring valid signed
 * objects linked to with valid domain objects.
 */
public interface ProvisioningCmsValidationStrategy {
    /**
     * Validates the incoming cms object. For an CMS object to be valid it needs to be, at least:
     *   * signed by the identity certificate of the child
     *   * the identity certificate needs to be currently valid
     *   * the EE cert needs to be currently valid
     *
     * The signature of the SignedData in the CMS by the EE certificate is already checked when parsing the CMS object by
     * <pre>net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectParser#verifySignature</pre>.
     *
     * @param unvalidatedProvisioningObject cms object that has been validated for structure (parsed) but not yet validated
     * @param provisioningIdentityCertificate identity certificate used to sign the object
     * @throws ProvisioningException when certificate or identity certificate are not valid
     */
    void validateProvisioningCmsAndIdentityCertificate(ProvisioningCmsObject unvalidatedProvisioningObject, ProvisioningIdentityCertificate provisioningIdentityCertificate) throws ProvisioningException;
}
