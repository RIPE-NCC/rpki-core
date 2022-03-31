package net.ripe.rpki.domain;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequestSerializer;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponseSerializer;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.security.auth.x500.X500Principal;

import java.nio.charset.Charset;
import java.security.PublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NonHostedCertificateAuthorityTest {

    private PregeneratedKeyPairFactory keyPairFactory = PregeneratedKeyPairFactory.getInstance();

    @Test
    void findOrCreatePublicKeyEntityByPublicKey_should_limit_number_of_public_keys() {
        NonHostedCertificateAuthority subject = new NonHostedCertificateAuthority(12, new X500Principal("CN=test"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, null);
        for (int i = 0; i < NonHostedCertificateAuthority.PUBLIC_KEY_LIMIT - 1; ++i) {
            PublicKey publicKey = keyPairFactory.generate().getPublic();
            subject.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        }

        PublicKeyEntity lastAdded = subject.findOrCreatePublicKeyEntityByPublicKey(keyPairFactory.generate().getPublic());

        assertThat(subject.findOrCreatePublicKeyEntityByPublicKey(lastAdded.getPublicKey()))
            .isSameAs(lastAdded);
        assertThatThrownBy(() -> subject.findOrCreatePublicKeyEntityByPublicKey(keyPairFactory.generate().getPublic()))
            .isInstanceOf(CertificationResourceLimitExceededException.class);
    }

    @Test
    void shouldAddNonHostedPublisherRepository() {
        NonHostedCertificateAuthority subject = new NonHostedCertificateAuthority(12, new X500Principal("CN=test"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, null);

        String requestXML = readFromFile("/repository-publisher/publisher_request.xml");
        PublisherRequest publisherRequest = new PublisherRequestSerializer().deserialize(requestXML);
        RepositoryResponse repositoryResponse = new RepositoryResponseSerializer().deserialize(readFromFile(
                "/repository-publisher/repository_response.xml"));

        subject.addNonHostedPublisherRepository(UUID.randomUUID(), publisherRequest, repositoryResponse);

        assertThat(subject.getPublisherRepositories().size()).isEqualTo(1);

    }

    @SneakyThrows
    private String readFromFile(String resourcePath) {
        final Resource resource = new ClassPathResource(resourcePath);
        return Resources.toString(resource.getURL(), Charset.defaultCharset());
    }
}
