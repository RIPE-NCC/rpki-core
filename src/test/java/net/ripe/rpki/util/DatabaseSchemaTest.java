package net.ripe.rpki.util;

import net.ripe.rpki.domain.CertificationDomainTestCase;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

public class DatabaseSchemaTest extends CertificationDomainTestCase {

    /**
     * {@see https://www.cybertec-postgresql.com/en/index-your-foreign-key/} for more information why.
     */
    @Test
    public void should_have_index_on_all_foreign_key_source_columns() {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery("SELECT c.conrelid\\:\\:regclass\\:\\:text AS \"table\",\n" +
            "       /* list of key column names in order */\n" +
            "       string_agg(a.attname, ',' ORDER BY x.n) AS columns,\n" +
            "       pg_catalog.pg_size_pretty(\n" +
            "          pg_catalog.pg_relation_size(c.conrelid)\n" +
            "       ) AS size,\n" +
            "       c.conname AS constraint,\n" +
            "       c.confrelid\\:\\:regclass\\:\\:text AS referenced_table\n" +
            "FROM pg_catalog.pg_constraint c\n" +
            "   /* enumerated key column numbers per foreign key */\n" +
            "   CROSS JOIN LATERAL\n" +
            "      unnest(c.conkey) WITH ORDINALITY AS x(attnum, n)\n" +
            "   /* name for each key column */\n" +
            "   JOIN pg_catalog.pg_attribute a\n" +
            "      ON a.attnum = x.attnum\n" +
            "         AND a.attrelid = c.conrelid\n" +
            "WHERE NOT EXISTS\n" +
            "        /* is there a matching index for the constraint? */\n" +
            "        (SELECT 1 FROM pg_catalog.pg_index i\n" +
            "         WHERE i.indrelid = c.conrelid\n" +
            "           /* it must not be a partial index */\n" +
            "           AND i.indpred IS NULL\n" +
            "           /* the first index columns must be the same as the\n" +
            "              key columns, but order doesn't matter */\n" +
            "           AND (i.indkey\\:\\:smallint[])[0\\:cardinality(c.conkey)-1]\n" +
            "               OPERATOR(pg_catalog.@>) c.conkey)\n" +
            "  AND c.contype = 'f'\n" +
            "GROUP BY c.conrelid, c.conname, c.confrelid\n" +
            "ORDER BY pg_catalog.pg_relation_size(c.conrelid) DESC;").getResultList();

        final String FORMAT = "%-30s %-30s %-15s %-40s %-30s";
        String unexpected = results.stream().map(row -> format(FORMAT, row[0], row[1], row[2], row[3], row[4])).collect(Collectors.joining("\n"));
        assertEquals(format("missing indexes on:\n" + FORMAT + "\n%s", "table", "columns", "size", "constraint", "referenced_table", unexpected), Collections.emptyList(), results);
    }
}
