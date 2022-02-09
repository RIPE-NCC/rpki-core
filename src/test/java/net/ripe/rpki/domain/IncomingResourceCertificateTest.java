package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static net.ripe.rpki.domain.CertificationDomainTestCase.BASE_URI;
import static org.assertj.core.api.Assertions.assertThat;


public class IncomingResourceCertificateTest {

    public static final URI UPDATED_PUBLICATION_URI = URI.create("rsync://rsync.example.com/foo.cer");

    private KeyPairEntity keyPair;
    private IncomingResourceCertificate subject;

    @Before
    public void setUp() {
        keyPair = TestObjects.createActiveKeyPair("KEY");
        subject = TestObjects.createResourceCertificate(12L, keyPair);
    }

    @Test
    public void shouldUpdateCertificate() {
        X509ResourceCertificate updatedCertificate = TestObjects.createResourceCertificate(15L, keyPair,
            new ValidityPeriod(new DateTime(), new DateTime().plusDays(10)), IpResourceSet.ALL_PRIVATE_USE_RESOURCES,
            new X509CertificateInformationAccessDescriptor[] {
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                    BASE_URI),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                    BASE_URI.resolve(keyPair.getManifestFilename())),
            }).getCertificate();

        subject.update(updatedCertificate, UPDATED_PUBLICATION_URI);

        assertThat(subject.getPublicationUri()).isEqualTo(UPDATED_PUBLICATION_URI);
        assertThat(subject.getResources()).isEqualTo(IpResourceSet.ALL_PRIVATE_USE_RESOURCES);
        assertThat(subject.getSubject()).isEqualTo(updatedCertificate.getSubject());
    }

}
