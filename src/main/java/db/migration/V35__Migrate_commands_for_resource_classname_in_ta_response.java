package db.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class V35__Migrate_commands_for_resource_classname_in_ta_response extends RpkiJavaMigration {

    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        String xsl = RpkiJavaMigration.load("V35_Migrate_commands_for_resource_classname_in_ta_response.xsl");

        for (RowData row : findTrustAnchorResponseCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET command = ? where id = ?", transformed, row.id);
        }
    }

    private List<RowData> findTrustAnchorResponseCommands(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = 'ProcessTrustAnchorResponseCommand'", (rs, rowNum) -> {
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
