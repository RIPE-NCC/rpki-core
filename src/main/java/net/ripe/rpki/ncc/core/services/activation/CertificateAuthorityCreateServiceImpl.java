package net.ripe.rpki.ncc.core.services.activation;

import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.NameNotUniqueException;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
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

import java.util.UUID;

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
    public void createHostedCertificateAuthority(@NonNull X500Principal name) {
        // Check for existence before executing the command to avoid having an
        // exception logged by the `CommandServiceImpl`, which can cause monitoring
        // alerts when too many happen in a short time, like someone using a script
        // to ensure a set of CAs exist.
        //
        // This check isn't fool-proof (two concurrent callers could still cause
        // the exception in the command execution, but this is much rarer so shouldn't
        // case monitoring system alerts).
        if (caViewService.findCertificateAuthorityByName(name) != null) {
            throw new CertificateAuthorityNameNotUniqueException(name);
        }

        ImmutableResourceSet resources;
        try {
            resources = resourceLookupService.lookupMemberCaPotentialResources(name)
                .map(ResourceExtension::getResources)
                .orElse(ImmutableResourceSet.empty());
        } catch (ResourceInformationNotAvailableException e) {
            // No resource information available, defaulting to empty resources is safe since the CA does not exist
            // yet and no certificate will be revoked (or created).
            resources = ImmutableResourceSet.empty();
        }
        LOG.info("Creating Hosted CA: {} with initial resources '{}'", name, resources);
        provisionMember(name, resources, productionCaName);
    }

    @Override
    public void createNonHostedCertificateAuthority(@NonNull X500Principal name, @NonNull ProvisioningIdentityCertificate identityCertificate) {
        // Check for existence before executing the command to avoid having an
        // exception logged by the `CommandServiceImpl`, which can cause monitoring
        // alerts when too many happen in a short time, like someone using a script
        // to ensure a set of CAs exist.
        //
        // This check isn't fool-proof (two concurrent callers could still cause
        // the exception in the command execution, but this is much rarer so shouldn't
        // case monitoring system alerts).
        if (caViewService.findCertificateAuthorityByName(name) != null) {
            throw new CertificateAuthorityNameNotUniqueException(name);
        }

        ImmutableResourceSet resources;
        try {
            resources = resourceLookupService.lookupMemberCaPotentialResources(name)
                .map(ResourceExtension::getResources)
                .orElse(ImmutableResourceSet.empty());
        } catch (ResourceInformationNotAvailableException e) {
            // No resource information available, defaulting to empty resources is safe since the CA does not exist
            // yet and no certificate will be revoked (or created).
            resources = ImmutableResourceSet.empty();
        }
        LOG.info("Creating Non-Hosted CA: {} with initial resources '{}'", name, resources);
        provisionNonHostedMember(name, resources, productionCaName, identityCertificate);
    }

    void provisionMember(final X500Principal caName, final ImmutableResourceSet resources, final X500Principal productionCaName) {
        try {
            asAdmin(() -> {
                long parentCaId = caViewService.findSmallestIntermediateCa(productionCaName)
                    .map(id -> id.getVersionedId().getId())
                    .orElseGet(() -> findProductionCaId(productionCaName));
                commandService.execute(new ActivateHostedCertificateAuthorityCommand(commandService.getNextId(), caName, resources, parentCaId));
            });
        } catch (NameNotUniqueException e) {
            throw new CertificateAuthorityNameNotUniqueException(caName);
        }
    }

    void provisionNonHostedMember(final X500Principal caName, final ImmutableResourceSet resources,
                                  final X500Principal productionCaName, final ProvisioningIdentityCertificate identityCertificate) {
        asAdmin(() -> {
            long parentCaId = caViewService.findSmallestIntermediateCa(productionCaName)
                .map(id -> id.getVersionedId().getId())
                .orElseGet(() -> findProductionCaId(productionCaName));
            // We want to know UUID before creating CA to add the UUID to the command summary
            final UUID uuid = UUID.randomUUID();
            commandService.execute(new ActivateNonHostedCertificateAuthorityCommand(commandService.getNextId(),
                caName, uuid, resources, identityCertificate, parentCaId));
        });
    }

    private long findProductionCaId(X500Principal productionCaName) {
        Long productionCaId = caViewService.findCertificateAuthorityIdByTypeAndName(ProductionCertificateAuthority.class, productionCaName);
        Validate.notNull(productionCaId, "Production Certificate Authority '" + productionCaName.getName() + "' not found");
        return productionCaId;
    }

}
