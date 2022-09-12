package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
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
import net.ripe.rpki.services.impl.handlers.ChildParentCertificateUpdateSaga;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static net.ripe.rpki.services.impl.background.BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE;

@Service(ALL_CA_CERTIFICATE_UPDATE_SERVICE)
public class AllCaCertificateUpdateServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {
    private final int updateBatchSize;

    private final CertificateAuthorityViewService caViewService;
    private final CommandService commandService;
    private final ResourceCache resourceCache;
    private final RepositoryConfiguration repositoryConfiguration;
    private final TransactionTemplate transactionTemplate;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;


    public AllCaCertificateUpdateServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                             CertificateAuthorityViewService caViewService,
                                             CommandService commandService,
                                             ResourceCache resourceCache,
                                             RepositoryConfiguration repositoryConfiguration,
                                             TransactionTemplate transactionTemplate,
                                             CertificateAuthorityRepository certificateAuthorityRepository,
                                             ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga,
                                             @Value("${certificate.authority.update.batch.size:1000}") int updateBatchSize) {
        super(backgroundTaskRunner);
        this.caViewService = caViewService;
        this.commandService = commandService;
        this.resourceCache = resourceCache;
        this.repositoryConfiguration = repositoryConfiguration;
        this.transactionTemplate = transactionTemplate;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.childParentCertificateUpdateSaga = childParentCertificateUpdateSaga;
        this.updateBatchSize = updateBatchSize;
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
        runParallel(Stream.of(task(
            () -> commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(productionCa.getVersionedId(), Integer.MAX_VALUE)),
            ex -> log.error("Unable to update incoming resource certificate for CA '{}'", productionCa.getName(), ex)
        )));
    }

    private int updateMemberCas(CertificateAuthorityData productionCa) {
        AtomicInteger updatedCounter = new AtomicInteger(0);

        Collection<CaIdentity> allChildrenIds = caViewService.findAllChildrenIdsForCa(productionCa.getName());
        runParallel(allChildrenIds
            .stream()
            .map(member -> task(
                () -> updateChildCertificate(commandService, member, updatedCounter),
                ex -> log.error("Unable to update incoming resource certificate for CA '{}", member.getCaName(), ex)
            ))
        );

        log.info("updated {} incoming resource certificates of {} member CAs", updatedCounter.get(), allChildrenIds.size());

        return updatedCounter.get();
    }


    private void updateChildCertificate(CommandService commandService, CaIdentity member, AtomicInteger updatedCounter) {
        if (updatedCounter.get() >= updateBatchSize) {
            return;
        }
        CommandStatus status = commandService.execute(new UpdateAllIncomingResourceCertificatesCommand(member.getVersionedId(), Integer.MAX_VALUE));
        if (status.isHasEffect()) {
            updatedCounter.incrementAndGet();
        }
    }

}
