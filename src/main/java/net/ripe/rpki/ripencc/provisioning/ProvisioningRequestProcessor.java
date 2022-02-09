package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;

public interface ProvisioningRequestProcessor {

    ProvisioningCmsObject process(ProvisioningCmsObject request);

}
