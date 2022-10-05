
package db.migration;

import com.google.common.base.Verify;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static net.logstash.logback.argument.StructuredArguments.v;

@Slf4j
public class V113__remove_roas_and_alerts_without_resources_on_certificate extends RpkiJavaMigration {
    @Override
    public void migrate(NamedParameterJdbcTemplate jdbcTemplate) {
        final AtomicLong affectedRoaPrefixes = new AtomicLong();
        final AtomicLong affectedIgnoredAlerts = new AtomicLong();

        Integer totalIgnoredAlerts = jdbcTemplate.query("SELECT count(*) AS count FROM roa_alert_configuration_ignored", (rs, rowNum) -> rs.getInt("count")).get(0);
        Integer totalRoaPrefixes = jdbcTemplate.query("SELECT count(*) AS count FROM roaconfiguration_prefixes", (rs, rowNum) -> rs.getInt("count")).get(0);

        // Get all roa configurations
        List<HostedCaDTO> caData = jdbcTemplate.query(
                "SELECT *, rsc.resources as cache_resources FROM certificateauthority ca " +
                        "LEFT JOIN keypair kp ON kp.ca_id = ca.id " +
                        "LEFT JOIN resourcecertificate rc ON rc.requesting_ca_id = ca.id " +
                        "LEFT JOIN resource_cache rsc ON rsc.name = ca.name " +
                        "WHERE " +
                        "(kp.status IS NULL or kp.status = 'CURRENT') AND " +
                        "(rc.subject_keypair_id IS NULL or rc.subject_keypair_id = kp.id) AND " +
                        "(rc.status IS NULL or rc.status = 'CURRENT')" +
                        "AND (rc.type IS NULL or rc.type = 'INCOMING') " +
                        "AND ca.type = 'HOSTED'",
                (rs, rowNum) -> {
                    // Parse the resources from the certificate
                    byte[] encoded = rs.getBytes("encoded");
                    Long caId = rs.getLong("id");
                    String caName = rs.getString("name");
                    String cacheResources = Optional.ofNullable(rs.getString("cache_resources")).orElse("");

                    IpResourceSet resources = IpResourceSet.parse(cacheResources);
                    if (encoded != null) {
                        X509ResourceCertificateParser parser = new X509ResourceCertificateParser();
                        parser.parse("database", encoded);
                        X509ResourceCertificate certificate = parser.getCertificate();

                        final IpResourceSet certificateResources = certificate.getResources();

                        if (!certificateResources.equals(resources)) {
                            log.info("[{}] mismatch between resource cache and certificate, extending {} with {}", caName, resources, certificateResources);
                        }
                        resources.addAll(certificateResources);
                    }

                    return new HostedCaDTO(caId, caName, resources);
                });


        caData.forEach((HostedCaDTO ca) -> {
            final AtomicLong affectedRoas = new AtomicLong();
            cleanUpRoaPrefixesForCa(jdbcTemplate, ca, affectedRoas);

            if (affectedRoas.get() > 0) {
                log.info("[{}] Removed {} configured roa-prefixes and marking CA for update. Current resources: {}", ca.getName(), affectedRoas, ca.displayResources());
                jdbcTemplate.update("UPDATE certificateauthority SET manifest_and_crl_check_needed = true WHERE id = :id", new BeanPropertySqlParameterSource(ca));
                affectedRoaPrefixes.addAndGet(affectedRoas.get());
            }
        });
        log.info("{}: Removed a total of {} configured roa-prefixes for space that is no longer on corresponding certificates.", this.getClass().getCanonicalName(), affectedRoaPrefixes);

        caData.forEach((HostedCaDTO ca) -> {
            final AtomicLong affectedAlerts = new AtomicLong();
            cleanUpAlertsForCa(jdbcTemplate, ca, affectedAlerts);

            if (affectedAlerts.get() > 0) {
                log.info("[{}] Removed {} ignored prefixes that are not more- or less-specific than CA resources ({})", ca.getName(), affectedAlerts, ca.displayResources());
                affectedIgnoredAlerts.addAndGet(affectedAlerts.get());
            }
        });

        log.info("{}: Removed a total of {} alerts for prefixes that are not less- or more specific than CA certificate.", this.getClass().getCanonicalName(), affectedIgnoredAlerts);

        // Safety checks
        Verify.verify(totalIgnoredAlerts < 100 || (affectedIgnoredAlerts.get() < 0.33 * totalIgnoredAlerts), "Too many affected ignored alerts (%d out of %d)", affectedIgnoredAlerts.get(), totalIgnoredAlerts);
        Verify.verify(totalRoaPrefixes < 100 || (affectedRoaPrefixes.get() < 0.10 * totalRoaPrefixes), "Too many affected ROA prefixes (%d out of %d)", affectedRoaPrefixes, totalRoaPrefixes);
    }



    private void cleanUpAlertsForCa(NamedParameterJdbcTemplate jdbcTemplate, HostedCaDTO ca, AtomicLong affected) {
        List<RoaAlertIgnoreDTO> ignores = jdbcTemplate.query("SELECT * " +
                "FROM" +
                " roa_alert_configuration rac " +
                "INNER JOIN roa_alert_configuration_ignored raci ON rac.id = raci.roa_alert_configuration_id " +
                "WHERE " +
                "rac.certificateauthority_id = :id;",
                new BeanPropertySqlParameterSource(ca),
                (rs, rowNum) -> {
                    // roa_alert_configuration_id, asn, prefix are NOT NULL
                    final String prefix = rs.getString("prefix");
                    final String asn = rs.getString("asn");
                    final BigInteger roaAlertConfigurationId = rs.getBigDecimal("roa_alert_configuration_id").toBigIntegerExact();

                    return new RoaAlertIgnoreDTO(roaAlertConfigurationId, prefix, asn);
                });

        ignores.forEach((ignore) -> {
                    // Remove ignores that do not overlap with the resources on the certificate.
                    // - not part of the certified resources
                    // - and are also not a less-specific of the certified resources
                    final IpResourceSet overlap = new IpResourceSet(ca.getResources()); // be explicit about copying
                    overlap.retainAll(ignore.getResource());

                    if (overlap.isEmpty()) {
                        log.info("[{}] removing ignored alert for {} {}", ca.getName(), v("prefix", ignore.getPrefix()), v("asn", ignore.getAsn()), v("roaAlertConfigurationId", ignore.roaAlertConfigurationId));

                        final int updated = jdbcTemplate.update("DELETE FROM roa_alert_configuration_ignored " +
                                        "WHERE " +
                                        "roa_alert_configuration_id = :roaAlertConfigurationId AND " +
                                        "asn = :asn AND " +
                                        "prefix = :prefix",
                                new BeanPropertySqlParameterSource(ignore)
                        );
                        if (updated != 1) {
                            log.error("Updated {} rows (expected: 1), aborting.", updated);
                            throw new IllegalStateException("Expected one matching row, not " + affected);
                        }

                        affected.incrementAndGet();
                    }
            });
    }
    private void cleanUpRoaPrefixesForCa(NamedParameterJdbcTemplate jdbcTemplate, HostedCaDTO ca, AtomicLong affected) {
        // Get the RoaConfiguration for this CA
        List<RoaConfigurationPrefixDTO> roaConfigurations = jdbcTemplate.query(
                "SELECT * " +
                        "FROM roaconfiguration rc " +
                        "INNER JOIN roaconfiguration_prefixes rcp ON rc.id = rcp.roaconfiguration_id " +
                        "WHERE " +
                        "rc.certificateauthority_id = :id",
                new BeanPropertySqlParameterSource(ca),
                (rs, rowNum) -> {
                    // Types _should_ match up with RoaConfigurationPrefix class
                    final long roaConfigurationId = rs.getLong("roaconfiguration_id");
                    final BigDecimal asn = rs.getBigDecimal("asn");
                    final int prefixTypeId = rs.getInt("prefix_type_id");
                    final BigDecimal prefixStart = rs.getBigDecimal("prefix_start");
                    final BigDecimal prefixEnd = rs.getBigDecimal("prefix_end");
                    // signed on postgres side, no overflow
                    final Integer maximumLength = rs.getObject("maximum_length", Integer.class);

                    return new RoaConfigurationPrefixDTO(roaConfigurationId, asn, prefixTypeId, prefixStart, prefixEnd, maximumLength);
                });

        roaConfigurations.forEach(roaConfiguration -> {
            if (ca.getResources().contains(roaConfiguration.getResource())) {
                return;
            }
            // this prefix is outside the currently certified space
            log.info("[{}] removing roa prefix {} AS{}", ca.getName(), roaConfiguration.displayPrefix(), v("asn", roaConfiguration.asn), v("prefixStart", roaConfiguration.prefixStart), v("prefixEnd", roaConfiguration.prefixEnd), v("prefixTypeId", roaConfiguration.prefixTypeId), v("maxLength", roaConfiguration.maximumLength), kv("roaConfigurationId", roaConfiguration.roaConfigurationId));

            final int updated = jdbcTemplate.update("DELETE FROM roaconfiguration_prefixes " +
                            "WHERE " +
                            "roaconfiguration_id = :roaConfigurationId AND " +
                            "asn = :asn AND " +
                            "prefix_type_id = :prefixTypeId AND " +
                            "prefix_start = :prefixStart AND " +
                            "prefix_end = :prefixEnd AND " +
                            "(((maximum_length IS NULL) AND (:maximumLength IS NULL)) OR ((maximum_length IS NOT NULL) AND (maximum_length = :maximumLength)))",
                    new BeanPropertySqlParameterSource(roaConfiguration)
            );
            if (updated != 1) {
                log.error("Updated {} rows (expected: 1), aborting.", updated);
                throw new IllegalStateException("Expected one matching row, not " + affected);
            }

            affected.addAndGet(updated);
        });
    }

    @Value
    private static class HostedCaDTO {
        private final Long id;
        private final String name;
        private final IpResourceSet resources;
        /** Human readable description of resourceset. */
        public String displayResources() {
            return resources.isEmpty() ? "{}" : resources.toString();
        }

        public IpResourceSet getResources() {
            return new IpResourceSet(resources);
        }
    }

    @Value
    private static class RoaAlertIgnoreDTO {
        final BigInteger roaAlertConfigurationId;
        final String prefix;
        final String asn;

        public IpResourceSet getResource() {
            return IpResourceSet.parse(prefix);
        }
    }

    @Value
    private static class RoaConfigurationPrefixDTO {
        private final long roaConfigurationId;
        private final BigDecimal asn;
        private final int prefixTypeId;
        // Use BigDecimal here. When using BigInteger the BeanPropertySqlParamSource truncates the value
        // before executing the query [possible spring bug, 2022-08-26, TdK].
        private final BigDecimal prefixStart;
        private final BigDecimal prefixEnd;
        private final Integer maximumLength;

        public IpResourceRange getResource() {
            // Get the address family
            IpResourceType type = IpResourceType.values()[prefixTypeId];
            // Create the prefix
            return type.fromBigInteger(prefixStart.toBigIntegerExact()).upTo(type.fromBigInteger(prefixEnd.toBigIntegerExact()));
        }

        public String displayPrefix() {
            return maximumLength != null ? String.format("%s-%d", getResource(), maximumLength) : getResource().toString();
        }
    }
}
