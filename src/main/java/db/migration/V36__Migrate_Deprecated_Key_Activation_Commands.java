package db.migration;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;

public class V36__Migrate_Deprecated_Key_Activation_Commands extends RpkiJavaMigration {

    @Override
    public void migrate(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        migrateAutoKeyRollCommands(jdbcTemplate);
        migrateKeyActivationCommands(jdbcTemplate);
    }

    private void migrateAutoKeyRollCommands(NamedParameterJdbcTemplate jdbcTemplate) throws IOException, TransformerException {
        String xsl = RpkiJavaMigration.load("V36_Migrate_deprecated_key_activation_commands.xsl");

        for (RowData row : findAutoKeyRollCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'KeyManagementActivatePendingKeysCommand', command = :command where id = :id",
                    new MapSqlParameterSource()
                            .addValue("command", transformed)
                            .addValue("id", row.id));
        }
    }

    private List<RowData> findAutoKeyRollCommands(NamedParameterJdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = 'AutoKeyRolloverChildCaCommand'", (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String command = rs.getString("command");
            return new RowData(id, command);
        });
    }

    private void migrateKeyActivationCommands(NamedParameterJdbcTemplate jdbcTemplate) throws IOException, TransformerException {
        String xsl = RpkiJavaMigration.load("V36_Migrate_deprecated_key_activation_commands.xsl");

        for (RowData row : findActivateKeyPairCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'KeyManagementActivatePendingKeysCommand', command = :command where id = :id",
                    new MapSqlParameterSource().addValue("command", transformed).addValue("id", row.id));
        }
    }

    private List<RowData> findActivateKeyPairCommands(NamedParameterJdbcTemplate jdbcTemplate) {
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
