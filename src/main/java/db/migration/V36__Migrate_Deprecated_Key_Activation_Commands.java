package db.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;

public class V36__Migrate_Deprecated_Key_Activation_Commands extends RpkiJavaMigration {

    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        migrateAutoKeyRollCommands(jdbcTemplate);
        migrateKeyActivationCommands(jdbcTemplate);
    }

    private void migrateAutoKeyRollCommands(JdbcTemplate jdbcTemplate) throws IOException, TransformerException {
        String xsl = RpkiJavaMigration.load("V36_Migrate_deprecated_key_activation_commands.xsl");

        for (RowData row : findAutoKeyRollCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'KeyManagementActivatePendingKeysCommand', command = ? where id = ?", transformed, row.id);
        }
    }

    private List<RowData> findAutoKeyRollCommands(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = 'AutoKeyRolloverChildCaCommand'", (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String command = rs.getString("command");
            return new RowData(id, command);
        });
    }

    private void migrateKeyActivationCommands(JdbcTemplate jdbcTemplate) throws IOException, TransformerException {
        String xsl = RpkiJavaMigration.load("V36_Migrate_deprecated_key_activation_commands.xsl");

        for (RowData row : findActivateKeyPairCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'KeyManagementActivatePendingKeysCommand', command = ? where id = ?", transformed, row.id);
        }
    }

    private List<RowData> findActivateKeyPairCommands(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = 'ActivatePendingKeypairCommand'", (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String command = rs.getString("command");
            return new RowData(id, command);
        });
    }

    private class RowData {
        public Long id;
        public String command;
        private RowData(Long id, String command) {
            this.id = id;
            this.command = command;
        }
    }
}
