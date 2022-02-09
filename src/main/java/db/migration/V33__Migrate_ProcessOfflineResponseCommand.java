package db.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class V33__Migrate_ProcessOfflineResponseCommand extends RpkiJavaMigration {

    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        String xsl = RpkiJavaMigration.load("V1_ProcessOfflineResponseCommand.xsl");

        for (RowData row : findProcessOfflineResponseCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'ProcessTrustAnchorResponseCommand', command = ? where id = ?", transformed, row.id);
        }
    }

    private List<RowData> findProcessOfflineResponseCommands(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = 'ProcessOfflineResponseCommand'", (rs, rowNum) -> {
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
