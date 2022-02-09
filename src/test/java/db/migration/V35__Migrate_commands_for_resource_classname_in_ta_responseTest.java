package db.migration;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;

import static db.migration.RpkiJavaMigration.load;
import static db.migration.XmlTransformer.*;
import static org.custommonkey.xmlunit.XMLAssert.*;

public class V35__Migrate_commands_for_resource_classname_in_ta_responseTest {

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void should_migrate_deprecated_signing_response() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V35_Migrate_commands_for_resource_classname_in_ta_response.xsl");
        String xml = load("/xml/migrations/ProcessOfflineResponseCommand/SigningResponseV35_deprecated.xml");
        String expected = load("/xml/migrations/ProcessOfflineResponseCommand/SigningResponseV35_expected.xml");

        assertXMLEqual(expected, transform(xsl, xml));
    }

    @Test
    public void should_migrate_deprecated_revocation_response() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V35_Migrate_commands_for_resource_classname_in_ta_response.xsl");
        String xml = load("/xml/migrations/ProcessOfflineResponseCommand/RevocationResponseV35_deprecated.xml");
        String expected = load("/xml/migrations/ProcessOfflineResponseCommand/RevocationResponseV35_expected.xml");

        assertXMLEqual(expected, transform(xsl, xml));
    }
}
