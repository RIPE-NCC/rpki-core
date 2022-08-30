package db.migration;


import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

public class V33__Migrate_ProcessOfflineResponseCommand extends RpkiJavaMigration {

    @Override
    public void migrate(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        String xsl = RpkiJavaMigration.load("V1_ProcessOfflineResponseCommand.xsl");

        for (RowData row : findProcessOfflineResponseCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandtype = 'ProcessTrustAnchorResponseCommand', command = :command where id = :id",
                    new MapSqlParameterSource()
                            .addValue("command", transformed)
                            .addValue("id", row.id));
        }
    }

    private List<RowData> findProcessOfflineResponseCommands(NamedParameterJdbcTemplate jdbcTemplate) {
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
