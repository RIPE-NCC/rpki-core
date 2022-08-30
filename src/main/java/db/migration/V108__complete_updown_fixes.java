package db.migration;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.ripencc.support.persistence.ASN1ObjectIdentifierPersistenceConverter;
import net.ripe.rpki.ripencc.support.persistence.UriPersistenceConverter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.Map;

@Slf4j
public class V108__complete_updown_fixes extends RpkiJavaMigration {

    private ASN1ObjectIdentifierPersistenceConverter asn1ObjectIdentifierPersistenceConverter = new ASN1ObjectIdentifierPersistenceConverter();
    private UriPersistenceConverter uriPersistenceConverter = new UriPersistenceConverter();

    @Override
    public void migrate(NamedParameterJdbcTemplate jdbcTemplate) {
        updatePublicKeysWithCurrentCertificate(jdbcTemplate);
        updateRevokedPublicKeys(jdbcTemplate);
        updatePublicKeyTableDefinition(jdbcTemplate);
    }

    private void updatePublicKeysWithCurrentCertificate(NamedParameterJdbcTemplate jdbcTemplate) {
        jdbcTemplate.query(
            "SELECT pk.id AS pk_id, rc.encoded AS certificate\n" +
                "  FROM non_hosted_ca_public_key pk INNER JOIN resourcecertificate rc ON pk.id = rc.subject_public_key_id\n" +
                " WHERE pk.latest_provisioning_request_type IS NULL\n" +
                "   AND rc.type = 'OUTGOING' AND rc.status = 'CURRENT' AND NOT embedded\n",
            (rs, rowNum) -> {
                long pkId = rs.getLong("pk_id");
                byte[] encoded = rs.getBytes("certificate");

                log.info("migrating non-hosted public key with active current certificate {}", pkId);

                updatePublicKeyWithCurrentCertificate(jdbcTemplate, pkId, encoded);

                return null;
            }
        );
    }

    private void updatePublicKeyWithCurrentCertificate(NamedParameterJdbcTemplate jdbcTemplate, long pkId, byte[] encoded) {
        X509ResourceCertificateParser parser = new X509ResourceCertificateParser();
        parser.parse("database", encoded);
        X509ResourceCertificate certificate = parser.getCertificate();

        X509CertificateInformationAccessDescriptor[] sia = certificate.getSubjectInformationAccess();
        for (int i = 0; i < sia.length; i++) {
            X509CertificateInformationAccessDescriptor descriptor = sia[i];
            jdbcTemplate.update(
                "INSERT INTO non_hosted_ca_public_key_requested_sia (public_key_entity_id, index, method, location)\n" +
                    "VALUES (:pkEntityId, :index, :method, :location)\n",
                new MapSqlParameterSource()
                        .addValue("pkEntityId", pkId)
                        .addValue("index", i)
                        .addValue("method", asn1ObjectIdentifierPersistenceConverter.convertToDatabaseColumn(descriptor.getMethod()))
                        .addValue("location", uriPersistenceConverter.convertToDatabaseColumn(descriptor.getLocation()))
            );
        }

        jdbcTemplate.update(
            "UPDATE non_hosted_ca_public_key\n" +
                "   SET latest_provisioning_request_type = 'issue'\n" +
                " WHERE id = :id",
            Collections.singletonMap("id", pkId)
        );
    }

    private void updateRevokedPublicKeys(NamedParameterJdbcTemplate jdbcTemplate) {
        jdbcTemplate.getJdbcTemplate().update(
            "UPDATE non_hosted_ca_public_key pk\n" +
                "   SET latest_provisioning_request_type = 'revoke'\n" +
                " WHERE latest_provisioning_request_type IS NULL;\n"
        );
    }

    private void updatePublicKeyTableDefinition(NamedParameterJdbcTemplate jdbcTemplate) {
        jdbcTemplate.getJdbcTemplate().update(
            "ALTER TABLE non_hosted_ca_public_key\n" +
                "  DROP COLUMN revoked,\n" +
                "  ALTER COLUMN latest_provisioning_request_type SET NOT NULL;\n");
    }
}
