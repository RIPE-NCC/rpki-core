package db.migration;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;

import static db.migration.RpkiJavaMigration.load;
import static db.migration.XmlTransformer.*;
import static org.custommonkey.xmlunit.XMLAssert.*;

public class V33__Migrate_ProcessOfflineResponseCommandTest {

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void should_migrate_deprecated_signing_response() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V1_ProcessOfflineResponseCommand.xsl");
        String xml = load("/xml/migrations/ProcessOfflineResponseCommand/SigningResponse_deprecated.xml");
        String expected = load("/xml/migrations/ProcessOfflineResponseCommand/SigningResponse_expected.xml");

        assertXMLEqual(expected, transform(xsl, xml));
    }

    @Test
    public void should_migrate_deprecated_revocation_response() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V1_ProcessOfflineResponseCommand.xsl");
        String xml = load("/xml/migrations/ProcessOfflineResponseCommand/RevocationResponse_deprecated.xml");
        String expected = load("/xml/migrations/ProcessOfflineResponseCommand/RevocationResponse_expected.xml");

        assertXMLEqual(expected, transform(xsl, xml));
    }

    @Test
    public void should_migrate_deprecated_republish() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V1_ProcessOfflineResponseCommand.xsl");
        String xml = load("/xml/migrations/ProcessOfflineResponseCommand/Republish_deprecated.xml");
        String expected = load("/xml/migrations/ProcessOfflineResponseCommand/Republish_expected.xml");

        assertXMLEqual(expected, transform(xsl, xml));
    }

}
