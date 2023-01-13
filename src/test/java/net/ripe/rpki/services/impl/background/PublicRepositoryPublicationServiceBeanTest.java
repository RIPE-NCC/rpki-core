package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class PublicRepositoryPublicationServiceBeanTest extends CertificationDomainTestCase {

    @Inject
    private BackgroundTaskRunner backgroundTaskRunner;
    @Inject
    private TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;
    @Inject
    private PlatformTransactionManager transactionManager;
    private final SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();
    private CommandService commandService;

    private PublicRepositoryPublicationServiceBean subject;

    @Before
    public void setUp() {
        commandService = spy(super.commandService);

        transactionTemplate.executeWithoutResult(status -> {
            clearDatabase();
            createInitialisedProdCaWithRipeResources();
        });

        subject = new PublicRepositoryPublicationServiceBean(
            backgroundTaskRunner,
            commandService,
            certificateAuthorityRepository,
            manifestPublicationService,
            publishedObjectRepository,
            trustAnchorPublishedObjectRepository,
            entityManager,
            transactionManager,
            simpleMeterRegistry
        );
    }

    @Bean
    public CommandService commandService() {
        return commandService;
    }

    @Test
    public void should_ensure_all_manifests_are_up_to_date_before_publishing() {
        assertThat(outdatedCertificateAuthorities()).hasSize(1);
        assertThat(pendingObjects()).hasSize(1);
        assertThat(publishedObjects()).isEmpty();

        subject.runService(Collections.emptyMap());

        assertThat(outdatedCertificateAuthorities()).isEmpty();
        assertThat(pendingObjects()).isEmpty();
        assertThat(publishedObjects()).hasSize(3);
    }

    @Test
    public void should_ensure_all_manifests_are_up_to_date_within_publishing_transaction() {
        // Ignore the command, so this CA will be picked up during the publication transaction instead
        doReturn(CommandStatus.create()).when(commandService).execute(isA(IssueUpdatedManifestAndCrlCommand.class));

        assertThat(outdatedCertificateAuthorities()).hasSize(1);
        assertThat(pendingObjects()).hasSize(1);
        assertThat(publishedObjects()).isEmpty();

        subject.runService(Collections.emptyMap());

        assertThat(outdatedCertificateAuthorities()).isEmpty();
        assertThat(pendingObjects()).isEmpty();
        assertThat(publishedObjects()).hasSize(3);
    }

    private Collection<ManagedCertificateAuthority> outdatedCertificateAuthorities() {
        return certificateAuthorityRepository.findAllWithOutdatedManifests(UTC.dateTime(), Integer.MAX_VALUE);
    }

    private List<PublishedObjectEntry> pendingObjects() {
        return publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PENDING_STATUSES);
    }

    private List<PublishedObjectEntry> publishedObjects() {
        return publishedObjectRepository.findEntriesByPublicationStatus(EnumSet.of(PublicationStatus.PUBLISHED));
    }
}
