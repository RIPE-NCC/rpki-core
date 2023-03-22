package net.ripe.rpki.services.impl.background;

import com.google.common.base.Predicates;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;

@Service(ALL_CA_CERTIFICATE_UPDATE_SERVICE)
public class AllCaCertificateUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private final int updateBatchSize;

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final ResourceCache resourceCache;
    private final RepositoryConfiguration repositoryConfiguration;


    public AllCaCertificateUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                             CertificateAuthorityViewService caViewService,
                                             CommandService commandService,
                                             ResourceCache resourceCache,
                                             RepositoryConfiguration repositoryConfiguration,
                                             @Value("${certificate.authority.update.batch.size:1000}") int updateBatchSize) {
        super(backgroundTaskRunner);
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.resourceCache = resourceCache;
        this.repositoryConfiguration = repositoryConfiguration;
        this.updateBatchSize = updateBatchSize;
    }

    @Override
    public String getName() {
        return "All CA certificate update service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        runService(Predicates.alwaysTrue());
    }

    public void runService(Predicate<CaIdentity> certificateAuthorityFilter) {
        CertificateAuthorityData productionCa = verifyPreconditions();
        if (productionCa == null) {
            return;
        }

        updateProductionCa(productionCa);
        int updatedCount = updateMemberCas(productionCa, certificateAuthorityFilter);
        if (updatedCount > 0) {
            // Update the production CA again, in case over-claiming child certificates were updated to correctly
            // remove the over-claiming resources.
            updateProductionCa(productionCa);
        }
    }

    private CertificateAuthorityData verifyPreconditions() {
        resourceCache.verifyResourcesArePresent();

        CertificateAuthorityData allResourcesCa = caViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
        if (allResourcesCa == null) {
            log.error("All Resources Certificate Authority '{}' was not found.", repositoryConfiguration.getAllResourcesCaPrincipal().getName());
            return null;
        }

        final X500Principal productionCaPrincipal = repositoryConfiguration.getProductionCaPrincipal();
        CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        if (productionCa == null) {
            log.error("Production Certificate Authority '{}' not found.", productionCaPrincipal);
            return null;
        }
        return productionCa;
    }

    private void updateProductionCa(CertificateAuthorityData productionCa) {
        // NOTE: There's no update of potentially over-claiming CAs happening here,
        // since we are updating all member CAs anyway.
        runParallel(Stream.of(task(
            () -> commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(productionCa.getVersionedId(), Integer.MAX_VALUE)),
            ex -> log.error("Unable to update incoming resource certificate for CA '{}'", productionCa.getName(), ex)
        )));
    }

    private int updateMemberCas(CertificateAuthorityData productionCa, Predicate<CaIdentity> certificateAuthorityFilter) {
        AtomicInteger updatedCounter = new AtomicInteger(0);

        Collection<CaIdentity> allChildrenIds = caViewService.findAllChildrenIdsForCa(productionCa.getName());
        runParallel(allChildrenIds
            .stream()
            .filter(certificateAuthorityFilter)
            .map(member -> task(
                () -> updateChildCertificate(commandService, member, updatedCounter),
                ex -> log.error("Unable to update incoming resource certificate for CA '{}'", member.getCaName(), ex)
            ))
        );

        log.info("updated {} incoming resource certificates of {} member CAs", updatedCounter.get(), allChildrenIds.size());

        return updatedCounter.get();
    }

    private void updateChildCertificate(CommandService commandService, CaIdentity member, AtomicInteger updatedCounter) {
        if (updatedCounter.get() >= updateBatchSize) {
            return;
        }
        try {
            CommandStatus status = commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(member.getVersionedId(), Integer.MAX_VALUE));
            if (status.isHasEffect()) {
                updatedCounter.incrementAndGet();
            }
        } catch (EntityNotFoundException e) {
            // CA was deleted between the initial query and executing the command, ignore this exception. Note that the
            // command service already logs a warning, so no need to log anything else here.
            log.warn("failed to update all incoming resource certificates for CA '{}': {}", member.getCaName(), e.toString());
        }
    }

}
