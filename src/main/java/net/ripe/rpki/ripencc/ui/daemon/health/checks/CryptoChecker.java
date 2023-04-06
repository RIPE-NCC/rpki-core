package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateBuilder;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.Optional;

@Component
@Slf4j
public class CryptoChecker {

    private final CertificateAuthorityRepository caRepostiory;

    private final RepositoryConfiguration repositoryConfiguration;
    private final CertificationProviderConfigurationData providerConfigurationData;

    @Autowired
    public CryptoChecker(CertificateAuthorityRepository caRepository, RepositoryConfiguration repositoryConfiguration, CertificationProviderConfigurationData providerConfigurationData) {
        this.caRepostiory = caRepository;
        this.repositoryConfiguration = repositoryConfiguration;
        this.providerConfigurationData = providerConfigurationData;
    }

    @Transactional
    public Health.Status getHealthStatus() {
        // All Resources CA has a key on the FS, check that it can be loaded
        final AllResourcesCertificateAuthority allResourcesCa = caRepostiory.findAllResourcesCAByName(repositoryConfiguration.getAllResourcesCaPrincipal());
        final Health.Status status = getCaStatus("All Resources", allResourcesCa);
        if (status.isHealthy()) {
            // Production CA has its key
            final ProductionCertificateAuthority productionCA = caRepostiory.findRootCAByName(repositoryConfiguration.getProductionCaPrincipal());
            return getCaStatus("Production", productionCA);
        }
        return status;
    }

    private Health.Status getCaStatus(String name, ManagedCertificateAuthority ca) {
        if (ca == null) {
            return Health.warning(name + " CA doesn't exists yet.");
        }
        final Optional<KeyPairEntity> keyPair = ca.getKeyPairs().stream().findFirst();
        if (!keyPair.isPresent()) {
            return Health.warning(name + " CA doesn't have a key pair yet.");
        }
        try {
            final KeyPair kp = keyPair.get().getKeyPair();
            if (kp == null) {
                return Health.error("Crypto is borked for " + name + " CA: no key pair in the key store.");
            }
        } catch (Exception e) {
            return Health.error("Crypto is borked for " + name + " CA: " + e);
        }
        return Health.ok();
    }

    @Transactional
    public void checkCryptoWorks() {
        // All Resources CA has a key on the FS, check that it can be loaded
        final AllResourcesCertificateAuthority allResourcesCa = caRepostiory.findAllResourcesCAByName(repositoryConfiguration.getAllResourcesCaPrincipal());
        verifyCA("All Resources", allResourcesCa);

        final ProductionCertificateAuthority productionCA = caRepostiory.findRootCAByName(repositoryConfiguration.getProductionCaPrincipal());
        verifyCA("Production", productionCA);
    }

    private void verifyCA(String name, ManagedCertificateAuthority ca) {
        if (ca == null) {
            log.warn(name + " CA doesn't exists yet.");
            return;
        }

        final Optional<KeyPairEntity> keyPair = ca.getKeyPairs().stream().findFirst();
        if (!keyPair.isPresent() || !keyPair.map(KeyPairEntity::getKeyPair).isPresent()) {
            log.warn(name + " CA doesn't have a key pair yet.");
            return;
        }
        try {
            final ResourceCertificate rc = trySigning(keyPair.get());
            if (rc == null) {
                throw new BorkedCrypto("Could not issue a certificate for '" + name + "'");
            }
        } catch (Exception e) {
            throw new BorkedCrypto(e);
        }
    }

    private ResourceCertificate trySigning(final KeyPairEntity keyPair) throws URISyntaxException {
        // invent an artificial certificate here and try to sign it
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final URI uri = URI.create("rsync://localhost");
        final KeyPairFactory keyPairFactory = new KeyPairFactory(providerConfigurationData.getKeyPairGeneratorProvider());

        return new ResourceCertificateBuilder()
            .withSubjectDN(new X500Principal("CN=zz.subject"))
            .withIssuerDN(new X500Principal("CN=zz.issuer"))
            .withSerial(BigInteger.ONE)
            .withSubjectPublicKey(keyPairFactory.generate().getPublic())
            .withSigningKeyPair(keyPair)
            .withValidityPeriod(new ValidityPeriod(now, new DateTime(now.getYear() + 1, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC)))
            .withResources(ImmutableResourceSet.parse("10/8"))
            .withParentPublicationDirectory(new java.net.URI("/tmp"))
            .withCrlDistributionPoints(URI.create("rsync://localhost/crl.crl"))
            .withCa(true)
            .withEmbedded(false)
            .withoutAuthorityInformationAccess()
            .withFilename("test-certificate.cer")
            .withSubjectInformationAccess(
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, uri),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, uri.resolve("manifest.mft")))
            .build();
    }

    public static class BorkedCrypto extends RuntimeException {
        public BorkedCrypto(String message) {
            super(message);
        }

        public BorkedCrypto(Throwable cause) {
            super(cause);
        }
    }

}
