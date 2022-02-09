package db.migration;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;

import static db.migration.RpkiJavaMigration.load;
import static db.migration.XmlTransformer.*;
import static org.custommonkey.xmlunit.XMLAssert.*;


public class V36__Migrate_Deprecated_Key_Activation_CommandsTest {

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void should_migrate_deprecated_auto_key_roll_command_to_new_command() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V36_Migrate_deprecated_key_activation_commands.xsl");
        String xml = load("/xml/migrations/V36/old_auto_key_roll_command.xml");
        String expected = load("/xml/migrations/V36/key_management_initiate_roll_command.xml");

        String migrated = transform(xsl, xml);

        assertXMLEqual(expected, migrated);
    }

    @Test
    public void should_migrate_deprecated_key_activation_command_to_new_command() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V36_Migrate_deprecated_key_activation_commands.xsl");
        String xml = load("/xml/migrations/V36/old_activate_command.xml");
        String expected = load("/xml/migrations/V36/key_management_activate_pending_keys_command.xml");

        String migrated = transform(xsl, xml);

        assertXMLEqual(expected, migrated);
    }

    @Test
    public void should_migrate_deprecated_revoke_key_pair_command_to_new_command() throws Exception {
        String xsl = load("/net/ripe/rpki/db/migrations/V36_Migrate_deprecated_key_activation_commands.xsl");
        String xml = load("/xml/migrations/V36/old_revoke_key_pair_command.xml");
        String expected = load("/xml/migrations/V36/key_management_revoke_old_keys_command.xml");

        String migrated = transform(xsl, xml);

        assertXMLEqual(expected, migrated);
    }

}
