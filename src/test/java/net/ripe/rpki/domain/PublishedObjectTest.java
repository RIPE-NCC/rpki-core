package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Random;

import static net.ripe.rpki.domain.PublicationStatus.PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.TO_BE_PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.WITHDRAWN;
import static org.junit.Assert.assertEquals;

public class PublishedObjectTest {

    private static final ValidityPeriod VALIDITY_PERIOD = new ValidityPeriod(new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC).plusDays(1));

    private static final String FILENAME_CER = "filename.cer";
    public static final URI REPO_URI = URI.create("rsync://host/dir/");
    private static final KeyPairEntity ISSUING_KEY_PAIR = TestObjects.TEST_KEY_PAIR_2;

    private static final byte[] CONTENT = randomBytes(10);

    private PublishedObject subject;

    @Before
    public void setUp() {
        subject = new PublishedObject(ISSUING_KEY_PAIR, FILENAME_CER, CONTENT, true, REPO_URI, VALIDITY_PERIOD);
    }

    @Test
    public void should_initially_have_status_TO_BE_PUBLISHED() {
        assertEquals(TO_BE_PUBLISHED, subject.getStatus());
    }

    @Test
    public void should_have_status_PUBLISHED_after_publication() {
        subject.published();

        assertEquals(PUBLISHED, subject.getStatus());
    }

    @Test
    public void should_be_WITHDRAWN_immediately_if_not_published_yet() {
        subject.withdraw();

        assertEquals(WITHDRAWN, subject.getStatus());
    }

    @Test
    public void should_have_status_WITHDRAWN_after_successful_withdrawal() {
        subject.withdraw();

        subject.withdrawn();

        assertEquals(WITHDRAWN, subject.getStatus());
    }

    private static byte[] randomBytes(int n) {
        byte[] result = new byte[n];
        new Random().nextBytes(result);
        return result;
    }
}
