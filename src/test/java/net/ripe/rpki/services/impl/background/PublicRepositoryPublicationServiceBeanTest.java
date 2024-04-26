package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.commons.util.UTC;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.PublishedObjectEntry;
import org.junit.Before;
import org.junit.Test;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicRepositoryPublicationServiceBeanTest extends CertificationDomainTestCase {

    @Inject
    private PublicRepositoryPublicationServiceBean subject;

    @Before
    public void setUp() {
        inTx(() -> {
            clearDatabase();
            createInitialisedProdCaWithRipeResources();
        });
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

    private Collection<ManagedCertificateAuthority> outdatedCertificateAuthorities() {
        return certificateAuthorityRepository.findAllWithOutdatedManifests(true, UTC.dateTime(), Integer.MAX_VALUE);
    }

    private List<PublishedObjectEntry> pendingObjects() {
        return publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PENDING_STATUSES);
    }

    private List<PublishedObjectEntry> publishedObjects() {
        return publishedObjectRepository.findEntriesByPublicationStatus(EnumSet.of(PublicationStatus.PUBLISHED));
    }
}
