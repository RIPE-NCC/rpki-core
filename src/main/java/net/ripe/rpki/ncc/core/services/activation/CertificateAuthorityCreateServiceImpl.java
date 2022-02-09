package net.ripe.rpki.ncc.core.services.activation;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;

import static net.ripe.rpki.server.api.security.RunAsUserHolder.asAdmin;

@Service
public class CertificateAuthorityCreateServiceImpl implements CertificateAuthorityCreateService {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateAuthorityCreateServiceImpl.class);

    private final ResourceLookupService resourceLookupService;
    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final X500Principal productionCaName;

    @Autowired
    public CertificateAuthorityCreateServiceImpl(ResourceLookupService resourceLookupService,
                                                 CertificateAuthorityViewService caViewService,
                                                 CommandService commandService,
                                                 @Value("${" + RepositoryConfiguration.PRODUCTION_CA_NAME + "}") String productionCaName) {
        this.resourceLookupService = resourceLookupService;
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.productionCaName = new X500Principal(productionCaName);
    }


    @Override
    public void createHostedCertificateAuthority(X500Principal name) {
        Validate.notNull(name, "Name is required.");
        LOG.info("Creating Hosted CA: " + name);
        IpResourceSet resources = resourceLookupService.lookupMemberCaPotentialResources(name);
        provisionMember(name, resources, productionCaName);
    }

    @Override
    public void createNonHostedCertificateAuthority(X500Principal name, ProvisioningIdentityCertificate identityCertificate) {
        Validate.notNull(name, "Name is required.");
        Validate.notNull(identityCertificate, "Identity certificate is required.");
        LOG.info("Creating Non-Hosted CA: " + name);
        IpResourceSet resources = resourceLookupService.lookupMemberCaPotentialResources(name);
        provisionNonHostedMember(name, resources, productionCaName, identityCertificate);
    }

    void provisionMember(final X500Principal caName, final IpResourceSet resources, final X500Principal productionCaName) {
        try {
            asAdmin(() -> {
                Long productionCaId = findProductionCaId(productionCaName);
                commandService.execute(new ActivateCustomerCertificateAuthorityCommand(commandService.getNextId(), caName, resources, productionCaId));
            });
        } catch (NameNotUniqueException e) {
            throw new CertificateAuthorityNameNotUniqueException(caName);
        }
    }

    void provisionNonHostedMember(final X500Principal caName, final IpResourceSet resources,
                                  final X500Principal productionCaName, final ProvisioningIdentityCertificate identityCertificate) {
        asAdmin(() -> {
            Long productionCaId = findProductionCaId(productionCaName);
            commandService.execute(new ActivateNonHostedCertificateAuthorityCommand(commandService.getNextId(), caName, resources, identityCertificate, productionCaId));
        });
    }

    private Long findProductionCaId(X500Principal productionCaName) {
        Long productionCaId = caViewService.findCertificateAuthorityIdByTypeAndName(CertificateAuthorityType.ROOT, productionCaName);
        Validate.notNull(productionCaId, "Production Certificate Authority '" + productionCaName.getName() + "' not found");
        return productionCaId;
    }

}
