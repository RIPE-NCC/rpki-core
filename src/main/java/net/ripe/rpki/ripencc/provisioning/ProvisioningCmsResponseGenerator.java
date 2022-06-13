package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.util.UUID;

/**
 * Generate a CMS response signed by the production CA downstream communicator key. This class is transactional
 * since it uses domain classes, but read-only since we do not track generated CMS response objects in the
 * database.
 */
@Component
@Transactional(readOnly = true)
class ProvisioningCmsResponseGenerator {
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final KeyPairFactory keyPairFactory;

    public ProvisioningCmsResponseGenerator(
        CertificateAuthorityRepository certificateAuthorityRepository,
        KeyPairFactory keyPairFactory
    ) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.keyPairFactory = keyPairFactory;
    }

    public ProvisioningCmsObject createProvisioningCmsResponseObject(AbstractProvisioningResponsePayload response) {
        ProductionCertificateAuthority productionCertificateAuthority = certificateAuthorityRepository.findByTypeAndUuid(ProductionCertificateAuthority.class, UUID.fromString(response.getSender()), LockModeType.NONE);
        return productionCertificateAuthority.getMyDownStreamProvisioningCommunicator().createProvisioningCmsResponseObject(keyPairFactory, response);
    }
}
