package net.ripe.rpki.services.impl;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.commons.provisioning.identity.ParentIdentity;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.DownStreamProvisioningCommunicator;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import jakarta.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.net.URI;

@Service
@Transactional(readOnly = true)
public class ProvisioningIdentityViewServiceBean implements ProvisioningIdentityViewService {

    private final TransactionOperations transactionTemplate;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final RepositoryConfiguration repositoryConfiguration;
    private final CertificationConfiguration certificationConfiguration;

    @Inject
    public ProvisioningIdentityViewServiceBean(TransactionOperations transactionTemplate,
                                               CertificateAuthorityRepository certificateAuthorityRepository,
                                               RepositoryConfiguration repositoryConfiguration,
                                               CertificationConfiguration certificationConfiguration) {
        this.transactionTemplate = transactionTemplate;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.repositoryConfiguration = repositoryConfiguration;
        this.certificationConfiguration = certificationConfiguration;
    }

    @Override
    public ParentIdentity getParentIdentityForNonHostedCa(final X500Principal childName) {
        return transactionTemplate.execute(status -> RunAsUserHolder.asAdmin((RunAsUserHolder.Get<ParentIdentity>) () -> {
            ProductionCertificateAuthority productionCa = findProductionCa();
            String parentHandle = productionCa.getUuid().toString();
            ProvisioningIdentityCertificate parentIdCertificate = productionCa.getProvisioningIdentityCertificate();

            NonHostedCertificateAuthority childCa = certificateAuthorityRepository.findByTypeAndName(NonHostedCertificateAuthority.class, childName);
            if (childCa == null) {
                return null;
            }
            String childHandle = childCa.getUuid().toString();
            ProvisioningIdentityCertificate childIdCertificate = childCa.getProvisioningIdentityCertificate();

            URI upDownUrl = URI.create(certificationConfiguration.getProvisioningBaseUrl());

            return new ParentIdentity(upDownUrl, parentHandle, childHandle, parentIdCertificate);
        }));
    }

    @Override
    public ProvisioningIdentityCertificate findProvisioningIdentityMaterial() {
        ProductionCertificateAuthority productionCa = findProductionCa();
        if (productionCa == null) {
            return null;
        }
        DownStreamProvisioningCommunicator downStreamCommunicator = productionCa.getMyDownStreamProvisioningCommunicator();
        if (downStreamCommunicator == null) {
            return null;
        }
        return downStreamCommunicator.getProvisioningIdentityCertificate();
    }

    private ProductionCertificateAuthority findProductionCa() {
        try {
            X500Principal productionCa = repositoryConfiguration.getProductionCaPrincipal();
            return certificateAuthorityRepository.findRootCAByName(productionCa);
        } catch (ClassCastException e) {
            throw new NotImplementedException("Only implemented for ProductionCertificateAuthority", e);
        }
    }

}
