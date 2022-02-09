package db.migration;

import org.apache.commons.io.IOUtils;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class RpkiJavaMigration extends BaseJavaMigration {

    public static String load(String name) throws IOException {
        try {
            return IOUtils.toString(RpkiJavaMigration.class.getResourceAsStream(name), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return IOUtils.toString(RpkiJavaMigration.class.getResourceAsStream("/net/ripe/rpki/db/migrations/" + name), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        DataSource ds = new SingleConnectionDataSource(context.getConnection(), true);
        migrate(new JdbcTemplate(ds));
    }

    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        throw new FlywayException("RPKI Java migration must override #migrate(Content) or #migrate(JdbcTemplate)");
    }
}
