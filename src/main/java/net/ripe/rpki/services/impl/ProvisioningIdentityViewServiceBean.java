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

import javax.security.auth.x500.X500Principal;
import java.net.URI;

@Service
public class ProvisioningIdentityViewServiceBean implements ProvisioningIdentityViewService {

    private CertificateAuthorityRepository certificateAuthorityRepository;

    private RepositoryConfiguration repositoryConfiguration;
    private CertificationConfiguration certificationConfiguration;

    public ProvisioningIdentityViewServiceBean(CertificateAuthorityRepository certificateAuthorityRepository,
                                               RepositoryConfiguration repositoryConfiguration,
                                               CertificationConfiguration certificationConfiguration) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.repositoryConfiguration = repositoryConfiguration;
        this.certificationConfiguration = certificationConfiguration;
    }

    @Override
    public ParentIdentity getParentIdentityForNonHostedCa(final X500Principal childName) {
        return RunAsUserHolder.asAdmin((RunAsUserHolder.Get<ParentIdentity>) () -> {
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
        });
    }

    private ProductionCertificateAuthority findProductionCa() {
        try {
            X500Principal productionCa = repositoryConfiguration.getProductionCaPrincipal();
            return certificateAuthorityRepository.findRootCAByName(productionCa);
        } catch (ClassCastException e) {
            throw new NotImplementedException("Only implemented for ProductionCertificateAuthority", e);
        }
    }

    @Override
    public ProvisioningIdentityCertificate findProvisioningIdentityMaterial() {
        DownStreamProvisioningCommunicator downStreamCommunicator = findProductionCa().getMyDownStreamProvisioningCommunicator();
        return downStreamCommunicator != null ? downStreamCommunicator.getProvisioningIdentityCertificate() : null;
    }

}
