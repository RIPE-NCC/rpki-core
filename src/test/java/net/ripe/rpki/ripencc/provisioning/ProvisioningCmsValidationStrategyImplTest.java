package net.ripe.rpki.ripencc.provisioning;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectParser;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateParser;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class ProvisioningCmsValidationStrategyImplTest {
    private ProvisioningCmsValidationStrategy subject;
    @Mock
    private ProvisioningMetricsService provisioningMetricsService;

    private ProvisioningCmsObject ca1CmsObject;
    private ProvisioningIdentityCertificate ca1IdCert;
    private ProvisioningIdentityCertificate ca2IdCert;

    @SneakyThrows
    private ProvisioningCmsObject readProvisioningPDU(String resourcePath) {
        final Resource resource = new ClassPathResource(resourcePath);

        ProvisioningCmsObjectParser cmsParser = new ProvisioningCmsObjectParser();
        cmsParser.parseCms("cms", Resources.toByteArray(resource.getURL()));

        return cmsParser.getProvisioningCmsObject();
    }

    @SneakyThrows
    public ProvisioningIdentityCertificate readProvisioningIdentityCertificate(String resourcePath) {
        final Resource resource = new ClassPathResource(resourcePath);

        ProvisioningIdentityCertificateParser certificateParser = new ProvisioningIdentityCertificateParser();
        certificateParser.parse("id-cert", Resources.toByteArray(resource.getURL()));

        return certificateParser.getCertificate();
    }

    @BeforeEach
    public void setup() {
        subject = new ProvisioningCmsValidationStrategyImpl(provisioningMetricsService);

        // Validity periods and signatures are not validated when reading
        ca1CmsObject = readProvisioningPDU("interop/up-down/krill-ca1-list-pdu.der");
        ca1IdCert = readProvisioningIdentityCertificate("interop/up-down/krill-ca1-id-cert.der");
        ca2IdCert = readProvisioningIdentityCertificate("interop/up-down/krill-ca2-id-cert.der");
    }

    @AfterEach
    public void restoreClock() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_cms_sig_not_from_id_cert() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2022-01-11T12:39:46.000Z").getMillis());

        assertThat(ca1IdCert.getPublicKey()).isNotEqualTo(ca2IdCert.getPublicKey());

        // The id certificates are different. Internally the validator rejects the CMS contents signature,
        // the CRL signature, the SKI because they all mismatch.
        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca2IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.BadData.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }

    //
    // Test cases for validity period
    // - before id cert validity and EE validity
    // - before EE validity
    // - current
    // - EE expired
    // - both expired
    //

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_cms_ee_not_valid_yet() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2022-01-11T10:00:00Z").getMillis());

        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca1IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.BadData.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_both_certs_not_valid_yet() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2021-01-11T10:00:00Z").getMillis());

        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca1IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.BadData.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_current_certs() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2022-01-11T12:39:46.000Z").getMillis());

        subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca1IdCert);
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_rejects_current_certs_before_last_signing_time() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2022-01-11T12:39:46.000Z").getMillis());

        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.of(ca1CmsObject.getSigningTime().plusHours(1)), ca1IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.PotentialReplayAttack.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_cms_ee_expired() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2022-01-13T12:39:46.000Z").getMillis());

        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca1IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.BadData.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }

    @Test
    public void testValidateProvisioningCmsAndIdentityCertificate_two_expired_certs() throws IOException {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2040-01-11T10:00:00Z").getMillis());

        assertThatThrownBy(() -> subject.validateProvisioningCmsAndIdentityCertificate(ca1CmsObject, Optional.empty(), ca1IdCert))
                .asInstanceOf(InstanceOfAssertFactories.type(ProvisioningException.BadData.class))
                .satisfies(e -> assertThat(e.getHttpStatusCode()).isEqualTo(400));
    }
}
