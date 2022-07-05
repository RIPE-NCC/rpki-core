package net.ripe.rpki.services.impl.jpa;

import com.google.common.hash.HashCode;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectEntry;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.TrustAnchorPublishedObject;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms.hashContents;
import static net.ripe.rpki.domain.PublicationStatus.PENDING_STATUSES;
import static net.ripe.rpki.domain.PublicationStatus.PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.TO_BE_PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.TO_BE_WITHDRAWN;
import static net.ripe.rpki.domain.PublicationStatus.WITHDRAWN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Test that queries are syntactically correct and run against the database schema. Both the {@link JpaPublishedObjectRepository}
 * and {@link JpaTrustAnchorPublishedObjectRepository} are tested, as these should be unified at some point and have
 * overlapping functionality/queries.
 */
@Transactional
public class JpaPublishedObjectRepositoryTest extends CertificationDomainTestCase {

    private static final ValidityPeriod VALIDITY_PERIOD = new ValidityPeriod(new DateTime(DateTimeZone.UTC).minusDays(2), new DateTime(DateTimeZone.UTC).minusDays(1));

    @Autowired
    private TrustAnchorPublishedObjectRepository trustAnchorPublishedObjectRepository;

    private KeyPairEntity issuingKeyPair;
    private TrustAnchorPublishedObject toBePublishedTaObject;
    private PublishedObject toBePublishedObject;
    private PublishedObject publishedObject;

    @Before
    public void setUp() {
        clearDatabase();

        ProductionCertificateAuthority ca = createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        entityManager.persist(ca);

        issuingKeyPair = ca.getCurrentKeyPair();

        toBePublishedTaObject = new TrustAnchorPublishedObject(URI.create("rsync://rpki.example.com/ta"), new byte[]{0xa, 0xb, 0xc});

        toBePublishedObject = new PublishedObject(
            issuingKeyPair,
            "filename.cer",
            new byte[]{0x1, 0x2, 0x3},

            true,
            URI.create("rsync://rpki.example.com/repository"),
            VALIDITY_PERIOD
        );

        publishedObject = new PublishedObject(
            issuingKeyPair,
            "filename.crl",
            new byte[]{0x4, 0x5, 0x6},
            true,
            URI.create("rsync://rpki.example.com/repository"),
            VALIDITY_PERIOD
        );
        publishedObject.published();

        trustAnchorPublishedObjectRepository.add(toBePublishedTaObject);
        publishedObjectRepository.add(toBePublishedObject);
        publishedObjectRepository.add(publishedObject);
    }

    @Test
    public void findActiveTrustAnchorPublishedObjects() {
        assertEquals(Collections.singletonList(toBePublishedTaObject), trustAnchorPublishedObjectRepository.findActiveObjects());
    }

    @Test
    public void findActiveManifestEntries() {
        List<PublishedObject> entries = publishedObjectRepository.findActiveManifestEntries(issuingKeyPair);
        assertEquals(2, entries.size());
        assertTrue(entries.contains(toBePublishedObject));
        assertTrue(entries.contains(publishedObject));
        assertFalse(entries.contains(toBePublishedTaObject));
    }

    @Test
    public void findEntriesByPublicationStatus() {
        List<PublishedObjectEntry> publishedEntries = publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PUBLISHED_STATUSES);
        assertEquals(1, publishedEntries.size());
        PublishedObjectEntry publishedEntry = publishedEntries.get(0);
        assertEquals(PUBLISHED, publishedEntry.getStatus());
        assertArrayEquals(hashContents(publishedObject.getContent()), HashCode.fromString(publishedEntry.getSha256()).asBytes());

        List<PublishedObjectEntry> pendingEntries = publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PENDING_STATUSES);
        assertEquals(2, pendingEntries.size());
        PublishedObjectEntry pendingEntry = pendingEntries.get(0);
        assertEquals(TO_BE_PUBLISHED, pendingEntry.getStatus());
        assertArrayEquals(hashContents(toBePublishedObject.getContent()), HashCode.fromString(pendingEntry.getSha256()).asBytes());
    }

    @Test
    public void findCurrentlyPublishedObjects() {
        List<PublishedObjectData> publishedObjects = publishedObjectRepository.findCurrentlyPublishedObjects();
        assertEquals(1, publishedObjects.size());

        PublishedObjectData published = publishedObjects.get(0);
        assertEquals(publishedObject.getUri(), published.getUri());
        assertArrayEquals(publishedObject.getContent(), published.getContent());
    }

    @Test
    public void updatePublicationStatus() {
        assertEquals(2, publishedObjectRepository.findEntriesByPublicationStatus(PENDING_STATUSES).size());
        assertEquals(1, publishedObjectRepository.updatePublicationStatus());
        assertEquals(1, trustAnchorPublishedObjectRepository.updatePublicationStatus());

        entityManager.refresh(toBePublishedObject);
        assertEquals(PUBLISHED, toBePublishedObject.getStatus());
        entityManager.refresh(toBePublishedTaObject);
        assertEquals(PUBLISHED, toBePublishedTaObject.getStatus());
        assertEquals(3, publishedObjectRepository.findCurrentlyPublishedObjects().size());

        // No pending objects so no updates required.
        assertEquals(0, publishedObjectRepository.findEntriesByPublicationStatus(PENDING_STATUSES).size());
        assertEquals(0, publishedObjectRepository.updatePublicationStatus());
        assertEquals(0, trustAnchorPublishedObjectRepository.updatePublicationStatus());

        publishedObject.withdraw();
        assertEquals(1, publishedObjectRepository.findEntriesByPublicationStatus(PENDING_STATUSES).size());
        assertEquals(1, publishedObjectRepository.updatePublicationStatus());
        assertEquals(0, trustAnchorPublishedObjectRepository.updatePublicationStatus());
        assertEquals(2, publishedObjectRepository.findCurrentlyPublishedObjects().size());
    }

    @Test
    public void should_not_allow_multiple_published_objects_for_same_location() {
        PublishedObject extraObject = new PublishedObject(
            issuingKeyPair,
            toBePublishedObject.getFilename(),
            new byte[]{0xa, 0xb, 0xc},

            true,
            URI.create(toBePublishedObject.getDirectory()),
            VALIDITY_PERIOD
        );
        extraObject.published();
        toBePublishedObject.published();

        publishedObjectRepository.add(extraObject);

        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }

    @Test
    public void should_update_TO_BE_WITHDRAWN_before_TO_BE_PUBLISHED_for_unique_constraint_on_location() {
        PublishedObject toBeWithdrawnObject = new PublishedObject(
            issuingKeyPair,
            toBePublishedObject.getFilename(),
            new byte[]{0x1, 0x2, 0x3},

            true,
            URI.create(toBePublishedObject.getDirectory()),
            VALIDITY_PERIOD
        );
        toBeWithdrawnObject.published();
        toBeWithdrawnObject.withdraw();
        publishedObjectRepository.add(toBeWithdrawnObject);

        publishedObjectRepository.updatePublicationStatus();

        entityManager.refresh(toBeWithdrawnObject);
        entityManager.refresh(toBePublishedObject);

        assertEquals(WITHDRAWN, toBeWithdrawnObject.getStatus());
        assertEquals(PUBLISHED, toBePublishedObject.getStatus());
    }

    @Test
    public void withdrawAllForKeyPair() {
        publishedObjectRepository.withdrawAllForKeyPair(publishedObject.getIssuingKeyPair());

        entityManager.refresh(toBePublishedObject);
        assertEquals(WITHDRAWN, toBePublishedObject.getStatus());

        entityManager.refresh(publishedObject);
        assertEquals(TO_BE_WITHDRAWN, publishedObject.getStatus());
    }

    @Test
    public void withdrawAllForDeletedKeyPair() {
        publishedObjectRepository.withdrawAllForDeletedKeyPair(publishedObject.getIssuingKeyPair());

        entityManager.refresh(toBePublishedObject);
        assertEquals(WITHDRAWN, toBePublishedObject.getStatus());
        assertNull(toBePublishedObject.getIssuingKeyPair());

        entityManager.refresh(publishedObject);
        assertEquals(TO_BE_WITHDRAWN, publishedObject.getStatus());
        assertNull(publishedObject.getIssuingKeyPair());
    }
    @Test
    public void findObjectsWithoutValidityPeriod() {
        assertEquals(0, publishedObjectRepository.findObjectIdsWithoutValidityPeriod().size());
    }

    @Test
    public void deleteExpiredObjects() {
        DateTime expirationTime = DateTime.now().minusDays(1);

        assertEquals(0, publishedObjectRepository.deleteExpiredObjects(expirationTime));

        publishedObject.withdraw();
        publishedObject.withdrawn();

        assertEquals(1, publishedObjectRepository.deleteExpiredObjects(expirationTime));
    }
}
