package db.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;

public class V49__Migrate_command_history_from_xml_to_clear_text extends RpkiJavaMigration {

    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        transformCommandOfType("UpdateAllIncomingResourceCertificatesCommand", "Updated all incoming certificates.", jdbcTemplate);
        transformCommandOfType("KeyManagementInitiateRollCommand", "Initiated key roll over.", jdbcTemplate);
        transformCommandOfType("KeyManagementActivatePendingKeysCommand", "Activated pending keys.", jdbcTemplate);
        transformCommandOfType("KeyManagementRevokeOldKeysCommand", "Revoked old keys.", jdbcTemplate);
        transformCommandOfType("PublishSignedMaterialCommand", "Published signed material.", jdbcTemplate);
        transformCommandOfType("ExpireOutgoingResourceCertificatesCommand", "Expired resource certificates that are no longer valid.", jdbcTemplate);
        transformCommandOfType("DeleteExpiredEmbeddedOutgoingResourceCertificatesCommand", "Deleted embedded resource certificates that are expired.", jdbcTemplate);
        transformCommandOfType("CreateRootCertificateAuthorityCommand", "Created Production Certificate Authority.", jdbcTemplate);
        transformCommandOfType("DeleteCertificateAuthorityCommand", "Deleted Certificate Authority.", jdbcTemplate);
        transformCommandOfType("GenerateOfflineCARepublishRequestCommand", "Generated Offline CA Republish Request.", jdbcTemplate);
        transformCommandOfType("ProcessTrustAnchorResponseCommand", "Processed Trust Anchor Response.", jdbcTemplate);

        xslTransformCommandOfType("SubscribeToRoaAlertCommand", jdbcTemplate);
        xslTransformCommandOfType("UpdateRoaAlertIgnoredAnnouncedRoutesCommand", jdbcTemplate);
        xslTransformCommandOfType("UnsubscribeFromRoaAlertCommand", jdbcTemplate);
        xslTransformCommandOfType("ActivateCustomerCertificateAuthorityCommand", jdbcTemplate);
        xslTransformCommandOfType("UpdateCertificateAuthorityResourcesCommand", jdbcTemplate);
        xslTransformCommandOfType("UpdateRoaConfigurationCommand", jdbcTemplate);
        xslTransformCommandOfType("CreateRoaSpecificationCommand", jdbcTemplate);
        xslTransformCommandOfType("UpdateRoaSpecificationCommand", jdbcTemplate);
        xslTransformCommandOfType("DeleteRoaSpecificationCommand", jdbcTemplate);
    }

    private void transformCommandOfType(String commandType, String commandSummary, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("UPDATE commandaudit SET commandsummary = '" + commandSummary + "' where commandtype = '" + commandType + "'");
    }
    
    private void xslTransformCommandOfType(String commandType, JdbcTemplate jdbcTemplate) throws IOException, TransformerException {
        String xsl = RpkiJavaMigration.load("V49_" + commandType + ".xsl");
        for (RowData row : findCommandsOfType(commandType, jdbcTemplate)) {
            String transformed = XmlTransformer.transform(xsl, row.command);
            jdbcTemplate.update("UPDATE commandaudit SET commandsummary = ? where id = ?", transformed, row.id);
        }
    }

    private List<RowData> findCommandsOfType(String commandType, JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM commandaudit WHERE commandtype = '" + commandType + "'", (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String command = rs.getString("commandsummary");
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
