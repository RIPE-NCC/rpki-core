package net.ripe.rpki.services.impl.background;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CaIdentity;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.handlers.ChildParentCertificateUpdateSaga;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;

@Slf4j
@Service(ALL_CA_CERTIFICATE_UPDATE_SERVICE)
public class AllCaCertificateUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private static final int UPDATE_COUNT_LIMIT = 1000;

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final ResourceCache resourceCache;
    private final RepositoryConfiguration repositoryConfiguration;
    private final TransactionTemplate transactionTemplate;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;


    public AllCaCertificateUpdateServiceBean(ActiveNodeService activeNodeService,
                                             CertificateAuthorityViewService caViewService,
                                             CommandService commandService,
                                             ResourceCache resourceCache,
                                             RepositoryConfiguration repositoryConfiguration,
                                             TransactionTemplate transactionTemplate,
                                             CertificateAuthorityRepository certificateAuthorityRepository,
                                             ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga) {
        super(activeNodeService);
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.resourceCache = resourceCache;
        this.repositoryConfiguration = repositoryConfiguration;
        this.transactionTemplate = transactionTemplate;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    protected void runService() {
        CertificateAuthorityData productionCa = verifyPreconditions();
        if (productionCa == null) {
            return;
        }

        updateProductionCa(productionCa);
        int updatedCount = updateMemberCas(productionCa);
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
        commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(productionCa.getVersionedId(), Integer.MAX_VALUE));
    }

    private int updateMemberCas(CertificateAuthorityData productionCa) {
        AtomicInteger updatedCounter = new AtomicInteger(0);

        Collection<CaIdentity> allChildrenIds = caViewService.findAllChildrenIdsForCa(productionCa.getName());
        allChildrenIds
            .stream()
            .map(member ->
                executorService.submit(() -> updateChildCertificate(commandService, member, updatedCounter)))
            .collect(Collectors.toList())
            .forEach(f -> justGetIt(f));

        log.info("updated {} incoming resource certificates of {} member CAs", updatedCounter.get(), allChildrenIds.size());

        return updatedCounter.get();
    }


    // Create a separate pool to avoid blocking the whole
    // ForkJoinPool.default() with threads waiting for IO
    private static final ExecutorService executorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    private void updateChildCertificate(CommandService commandService, CaIdentity member, AtomicInteger updatedCounter) {
        try {
            if (updatedCounter.get() >= UPDATE_COUNT_LIMIT) {
                return;
            }
            CommandStatus status = commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(member.getVersionedId(), Integer.MAX_VALUE));
            if (status.isHasEffect()) {
                updatedCounter.incrementAndGet();
            }
        } catch (RuntimeException e) {
            log.error("Error for CA '{}': {}", member.getCaName().getPrincipal(), e.getMessage(), e);
        }
    }

    @SneakyThrows
    private static <T> T justGetIt(Future<T> f) {
        return f.get();
    }

}
