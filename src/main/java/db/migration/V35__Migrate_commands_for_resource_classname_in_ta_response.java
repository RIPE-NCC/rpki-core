package db.migration;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

public class V35__Migrate_commands_for_resource_classname_in_ta_response extends RpkiJavaMigration {

    @Override
    public void migrate(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        String xsl = RpkiJavaMigration.load("V35_Migrate_commands_for_resource_classname_in_ta_response.xsl");

        for (RowData row : findTrustAnchorResponseCommands(jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET command = :command where id = :id",
                    new MapSqlParameterSource()
                            .addValue("command", transformed)
                            .addValue("id", row.id)
            );
        }
    }

    private List<RowData> findTrustAnchorResponseCommands(NamedParameterJdbcTemplate jdbcTemplate) {
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
