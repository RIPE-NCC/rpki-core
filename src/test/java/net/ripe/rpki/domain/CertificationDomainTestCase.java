package net.ripe.rpki.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.manifest.ManifestPublicationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extend this class to run tests that need access to the Spring application, including a database and the REST API, etc.
 * <p>
 * If you just need test objects in pure unit tests, use the static methods in {@link TestObjects} instead.
 * </p>
 */
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public abstract class CertificationDomainTestCase {

    @Autowired
    protected RepositoryConfiguration repositoryConfiguration;

    @Autowired
    protected ResourceCertificateRepository resourceCertificateRepository;

    @Autowired
    @Named("jpaCertificateAuthorityRepository")
    protected CertificateAuthorityRepository certificateAuthorityRepository;

    @Autowired
    protected SingleUseEeCertificateFactory singleUseEeCertificateFactory;

    protected ManifestPublicationService manifestPublicationService;

    @Autowired
    protected CertificateRequestCreationService certificateRequestCreationService;

    @Autowired
    protected PublishedObjectRepository publishedObjectRepository;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected CrlEntityRepository crlEntityRepository;

    @Autowired
    protected ManifestEntityRepository manifestEntityRepository;

    @Autowired
    protected KeyPairService keyPairService;

    protected SingleUseKeyPairFactory singleUseKeyPairFactory = new SingleUseKeyPairFactory(PregeneratedKeyPairFactory.getInstance());

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected CommandService commandService;

    @Autowired
    protected ResourceCache resourceCache;

    protected SimpleMeterRegistry meterRegistry;

    @Before
    public void setupTest() {
        meterRegistry = new SimpleMeterRegistry();
        manifestPublicationService = new ManifestPublicationService(
            resourceCertificateRepository,
            publishedObjectRepository,
            crlEntityRepository,
            manifestEntityRepository,
            singleUseKeyPairFactory,
            singleUseEeCertificateFactory,
            meterRegistry
        );
        Environment.load();
    }

    protected void clearDatabase() {
        // Clean the test database. Note that this is not transactional, but the test database should be empty anyway.
        entityManager.createNativeQuery("TRUNCATE TABLE certificateauthority, commandaudit, ta_published_object, resource_cache, roaconfiguration CASCADE").executeUpdate();
        resourceCache.populateCache(Map.of(CaName.of(repositoryConfiguration.getProductionCaPrincipal()), ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES));
    }

    protected ProductionCertificateAuthority createInitializedAllResourcesAndProductionCertificateAuthority() {
        AllResourcesCertificateAuthority allResources = new AllResourcesCertificateAuthority(TestObjects.ACA_ID, TestObjects.ALL_RESOURCES_CA_NAME, UUID.randomUUID());
        KeyPairEntity acaKeyPair = keyPairService.createKeyPairEntity();
        allResources.addKeyPair(acaKeyPair);
        certificateAuthorityRepository.add(allResources);

        allResources.processCertifiableResources(keyPairService, certificateRequestCreationService);
        KeyPairEntity allResourcesKeyPair = allResources.getKeyPairs().iterator().next();
        CertificateIssuanceResponse response = TestObjects.makeSelfSignedCertificate(resourceCertificateRepository, repositoryConfiguration, allResourcesKeyPair,
            allResources.getName(), ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES);

        allResources.processCertificateIssuanceResponse(response, resourceCertificateRepository);
        allResources.setUpStreamCARequestEntity(null);
        assertThat(acaKeyPair.isCurrent()).isTrue();

        ProductionCertificateAuthority production = new ProductionCertificateAuthority(TestObjects.CA_ID, repositoryConfiguration.getProductionCaPrincipal(), UUID.randomUUID(), allResources);
        issueCertificateForNewKey(allResources, production, TestObjects.PRODUCTION_CA_RESOURCES);

        return production;
    }

    protected KeyPairEntity issueCertificateForNewKey(ManagedCertificateAuthority parent, ManagedCertificateAuthority child, ImmutableResourceSet requestedResources) {
        KeyPairEntity kp = keyPairService.createKeyPairEntity();
        child.addKeyPair(kp);
        certificateAuthorityRepository.add(child);

        CertificateIssuanceRequest issuanceRequest = (CertificateIssuanceRequest) child.processResourceClassListResponse(new ResourceClassListResponse(requestedResources), certificateRequestCreationService).get(0);
        CertificateIssuanceResponse issuanceResponse = parent.processCertificateIssuanceRequest(child, issuanceRequest, resourceCertificateRepository, Integer.MAX_VALUE);
        child.processCertificateIssuanceResponse(issuanceResponse, resourceCertificateRepository);

        assertThat(kp.isCurrent()).isTrue();
        return kp;
    }

    public ProductionCertificateAuthority createInitialisedProdCaWithRipeResources() {
        ProductionCertificateAuthority ca = TestObjects.createInitialisedProdCaWithRipeResources(certificateAuthorityRepository, resourceCertificateRepository, repositoryConfiguration);
        certificateAuthorityRepository.add(ca);
        return ca;
    }

    public KeyPairEntity createInitialisedProductionCaKeyPair(ProductionCertificateAuthority ca, String keyPairName) {
        return TestObjects.createInitialisedKeyPair(certificateAuthorityRepository, resourceCertificateRepository, repositoryConfiguration, ca, keyPairName);
    }

    protected void inTx(Runnable r) {
        transactionTemplate.executeWithoutResult((status) -> r.run());
    }

    protected <T> T withTx(Supplier<T> c) {
        return transactionTemplate.execute(transactionStatus -> c.get());
    }

    protected CommandStatus execute(CertificateAuthorityCommand command) {
        try {
            return commandService.execute(command);
        } finally {
            entityManager.flush();
        }
    }
}
