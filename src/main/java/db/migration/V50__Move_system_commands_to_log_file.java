package db.migration;

import net.ripe.rpki.commons.util.VersionedId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public class V50__Move_system_commands_to_log_file extends RpkiJavaMigration {

    private static final Logger LOG = LoggerFactory.getLogger("audit-log");

    @Override
    public void migrate(JdbcTemplate jdbcTemplate) {
        logAllCommands(jdbcTemplate);
        deleteSystemCommandsFromDatabase(jdbcTemplate);
    }

    private void logAllCommands(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.query("SELECT * FROM commandaudit ORDER BY executionTime", (rs, rowNum) -> {
            LOG.info("executionTime=" + rs.getTimestamp("executiontime") +
                    " principal=" + rs.getString("principal") +
                    " caId=" + new VersionedId(rs.getLong("ca_id"), rs.getLong("ca_version")) +
                    " commandType=" + rs.getString("commandtype") +
                    " commandGroup=" + rs.getString("commandgroup") +
                    " commandSummary=" + rs.getString("commandsummary")
            );
            return null;
        });
    }

    private void deleteSystemCommandsFromDatabase(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE from commandaudit where commandgroup = 'SYSTEM'");
    }
}
