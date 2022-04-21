package net.ripe.rpki.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.application.impl.ResourceCertificateInformationAccessStrategyBean;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementServiceImpl;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.util.DBComponent;
import net.ripe.rpki.util.MemoryDBComponent;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.function.Supplier;

import static net.ripe.rpki.domain.CertificateAuthority.GRACEPERIOD;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public abstract class CertificationDomainTestCase {
    public static final long CA_ID = 42L;
    public static final long ACA_ID = 45L;

    protected static final X500Principal PRODUCTION_CA_NAME = new X500Principal("O=ORG-TEST-PRODUCTION-CA");
    public static final X500Principal ALL_RESOURCES_CA_NAME = new X500Principal("CN=Test All Resources CA");

    public static final IpResourceSet DEFAULT_PRODUCTION_CA_RESOURCES = IpResourceSet.ALL_PRIVATE_USE_RESOURCES;

    public static final IpResourceSet PRODUCTION_CA_RESOURCES = IpResourceSet.ALL_PRIVATE_USE_RESOURCES;

    public static final URI BASE_URI = URI.create("rsync://localhost:20873/repository/");

    @Autowired
    protected RepositoryConfiguration repositoryConfiguration;

    @Autowired
    protected ResourceCertificateRepository resourceCertificateRepository;

    @Autowired
    @Named("jpaCertificateAuthorityRepository")
    protected CertificateAuthorityRepository certificateAuthorityRepository;

    protected CertificateManagementService certificateManagementService;

    @Autowired
    protected CertificateRequestCreationService certificateRequestCreationService;

    @Autowired
    protected PublishedObjectRepository publishedObjectRepository;

    @Autowired
    protected MemoryDBComponent dbComponent;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected CrlEntityRepository crlEntityRepository;

    @Autowired
    protected ManifestEntityRepository manifestEntityRepository;

    @Autowired
    protected KeyPairService keyPairService;

    protected KeyPairFactory keyPairFactory = PregeneratedKeyPairFactory.getInstance();

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected CommandService commandService;

    protected SimpleMeterRegistry meterRegistry;

    @Before
    public void setupTest() {
        meterRegistry = new SimpleMeterRegistry();
        certificateManagementService = new CertificateManagementServiceImpl(resourceCertificateRepository, publishedObjectRepository, dbComponent, crlEntityRepository, manifestEntityRepository, keyPairFactory, meterRegistry);
        Environment.load();
    }

    protected void clearDatabase() {
        // Clean the test database. Note that this is not transactional, but the test database should be empty anyway.
        entityManager.createNativeQuery("TRUNCATE TABLE certificateauthority, ta_published_object CASCADE").executeUpdate();
    }

    protected ProductionCertificateAuthority createInitializedAllResourcesAndProductionCertificateAuthority() {
        AllResourcesCertificateAuthority allResources = new AllResourcesCertificateAuthority(ACA_ID, ALL_RESOURCES_CA_NAME, 1);
        KeyPairEntity acaKeyPair = keyPairService.createKeyPairEntity("ACA-TEST-KEY");
        allResources.addKeyPair(acaKeyPair);
        certificateAuthorityRepository.add(allResources);

        allResources.processCertifiableResources(keyPairService, certificateRequestCreationService);
        KeyPairEntity allResourcesKeyPair = allResources.getKeyPairs().iterator().next();
        CertificateIssuanceResponse response = makeSelfSignedCertificate(certificateManagementService, repositoryConfiguration, allResources, allResourcesKeyPair,
            allResources.getName(), IpResourceSet.ALL_PRIVATE_USE_RESOURCES, dbComponent);

        allResources.updateIncomingResourceCertificate(allResourcesKeyPair, response.getCertificate(), response.getPublicationUri());
        allResources.setUpStreamCARequestEntity(null);
        assertThat(acaKeyPair.isCurrent()).isTrue();

        ProductionCertificateAuthority production = new ProductionCertificateAuthority(CA_ID, repositoryConfiguration.getProductionCaPrincipal(), allResources, 1);
        KeyPairEntity productionKeyPair = keyPairService.createKeyPairEntity("TEST-KEY");
        production.addKeyPair(productionKeyPair);
        certificateAuthorityRepository.add(production);

        CertificateIssuanceRequest issuanceRequest = (CertificateIssuanceRequest) production.processResourceClassListResponse(new ResourceClassListResponse(PRODUCTION_CA_RESOURCES), keyPairService, certificateRequestCreationService).get(0);
        CertificateIssuanceResponse issuanceResponse = allResources.processCertificateIssuanceRequest(production, issuanceRequest, resourceCertificateRepository, dbComponent, Integer.MAX_VALUE);
        production.processCertificateIssuanceResponse(issuanceResponse, resourceCertificateRepository);
        assertThat(productionKeyPair.isCurrent()).isTrue();

        return production;
    }

    protected static ProductionCertificateAuthority createProductionCertificateAuthority(long id, X500Principal name) {
        return createProductionCertificateAuthority(id, name, PRODUCTION_CA_RESOURCES);
    }

    protected static ProductionCertificateAuthority createProductionCertificateAuthority(long id, X500Principal name, IpResourceSet resources) {
        return new ProductionCertificateAuthority(id, name, null, 1);
    }

    protected static AllResourcesCertificateAuthority createAllResourcesCertificateAuthority(long id, X500Principal name) {
        return new AllResourcesCertificateAuthority(id, name, 1);
    }

    public static ProductionCertificateAuthority createInitialisedProdCaWithRipeResources(CertificateManagementService certificateManagementService) {
        return createInitialisedProdCaWithRipeResources(certificateManagementService, new MemoryDBComponent());
    }

    public static ProductionCertificateAuthority createInitialisedProdOrgCaWithRipeResources(CertificateManagementService certificateManagementService) {
        return createInitialisedProdCaWithRipeResources(certificateManagementService, new MemoryDBComponent(), PRODUCTION_CA_NAME);
    }

    public static ProductionCertificateAuthority createInitialisedProdCaWithRipeResources(CertificateManagementService certificateManagementService, DBComponent dbComponent) {
        return createInitialisedProdCaWithRipeResources(certificateManagementService, dbComponent, PRODUCTION_CA_NAME);
    }

    public static ProductionCertificateAuthority createInitialisedProdCaWithRipeResources(CertificateManagementService certificateManagementService,
                                                                                          DBComponent dbComponent,
                                                                                          X500Principal principal) {
        RepositoryConfiguration certificationConfiguration = mock(RepositoryConfiguration.class);
        when(certificationConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(certificationConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        CertificateRequestCreationService certificateRequestCreationService = new CertificateRequestCreationServiceBean(certificationConfiguration);
        ProductionCertificateAuthority ca = createProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, PRODUCTION_CA_RESOURCES);
        KeyPairEntity kp = TestObjects.createActiveKeyPair("TEST-KEY");

        KeyPairService keyPairService = mock(KeyPairService.class);
        ca.addKeyPair(kp);

        ca.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = ca.getUpStreamCARequestEntity().getUpStreamCARequest();
        SigningRequest request = (SigningRequest) trustAnchorRequest.getTaRequests().get(0);

        CertificateIssuanceResponse response = makeSelfSignedCertificate(certificateManagementService, certificationConfiguration, ca, kp,
                request.getResourceCertificateRequest().getSubjectDN(), IpResourceSet.ALL_PRIVATE_USE_RESOURCES, dbComponent);

        ca.updateIncomingResourceCertificate(kp, response.getCertificate(), response.getPublicationUri());
        ca.setUpStreamCARequestEntity(null);

        Validate.isTrue(kp.isCurrent());

        return ca;
    }

    public ProductionCertificateAuthority createInitialisedProdCaWithRipeResources() {
        RepositoryConfiguration certificationConfiguration = mock(RepositoryConfiguration.class);
        when(certificationConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(certificationConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        CertificateRequestCreationService certificateRequestCreationService = new CertificateRequestCreationServiceBean(certificationConfiguration);
        ProductionCertificateAuthority ca = createProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, PRODUCTION_CA_RESOURCES);

        KeyPairEntity kp = createInitialisedProductionCaKeyPair(certificateRequestCreationService, ca, "TEST-KEY");

        Validate.isTrue(kp.isCurrent());

        return ca;
    }

    public KeyPairEntity createInitialisedProductionCaKeyPair(CertificateRequestCreationService certificateRequestCreationService, ProductionCertificateAuthority ca, String keyPairName) {
        KeyPairEntity kp = TestObjects.createTestKeyPair(keyPairName);
        ca.addKeyPair(kp);
        certificateAuthorityRepository.add(ca);

        ca.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = ca.getUpStreamCARequestEntity().getUpStreamCARequest();
        SigningRequest request = (SigningRequest) trustAnchorRequest.getTaRequests().get(0);

        CertificateIssuanceResponse response = makeSelfSignedCertificate(certificateManagementService, repositoryConfiguration, ca, kp,
                request.getResourceCertificateRequest().getSubjectDN(), IpResourceSet.ALL_PRIVATE_USE_RESOURCES, dbComponent);

        ca.updateIncomingResourceCertificate(kp, response.getCertificate(), response.getPublicationUri());
        ca.setUpStreamCARequestEntity(null);

        return kp;
    }

    public static AllResourcesCertificateAuthority createInitialisedAllResourcesCaWithRipeResources(CertificateManagementService certificateManagementService) {
        return createInitialisedAllResourcesCaWithRipeResources(certificateManagementService, new MemoryDBComponent());
    }

    public static AllResourcesCertificateAuthority createInitialisedAllResourcesCaWithRipeResources(CertificateManagementService certificateManagementService, DBComponent dbComponent) {
        final AllResourcesCertificateAuthority allResourcesCertificateAuthority = createAllResourcesCertificateAuthority(ACA_ID, ALL_RESOURCES_CA_NAME);
        RepositoryConfiguration certificationConfiguration = mock(RepositoryConfiguration.class);
        when(certificationConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(certificationConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        CertificateRequestCreationService certificateRequestCreationService = new CertificateRequestCreationServiceBean(certificationConfiguration);
        ProductionCertificateAuthority ca = new ProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, allResourcesCertificateAuthority, 1);
        KeyPairEntity kp = TestObjects.createActiveKeyPair("TEST-KEY");

        KeyPairService keyPairService = mock(KeyPairService.class);
        ca.addKeyPair(kp);

        ca.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = ca.getUpStreamCARequestEntity().getUpStreamCARequest();
        SigningRequest request = (SigningRequest) trustAnchorRequest.getTaRequests().get(0);

        CertificateIssuanceResponse response = makeSelfSignedCertificate(certificateManagementService, certificationConfiguration, ca, kp, request.getResourceCertificateRequest().getSubjectDN(), IpResourceSet.ALL_PRIVATE_USE_RESOURCES, dbComponent);

        ca.updateIncomingResourceCertificate(kp, response.getCertificate(), response.getPublicationUri());
        ca.setUpStreamCARequestEntity(null);

        Validate.isTrue(kp.isCurrent());

        return allResourcesCertificateAuthority;
    }


    private static CertificateIssuanceResponse makeSelfSignedCertificate(CertificateManagementService certificateManagementService,
                                                                         RepositoryConfiguration configuration,
                                                                         HostedCertificateAuthority ca,
                                                                         KeyPairEntity signingKeyPair,
                                                                         X500Principal subject,
                                                                         IpResourceSet resources, DBComponent dbComponent) {
        DateTime now = new DateTime(DateTimeZone.UTC);
        ResourceCertificateInformationAccessStrategy ias = new ResourceCertificateInformationAccessStrategyBean();
        X509CertificateInformationAccessDescriptor[] sia = {
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                        configuration.getPublicRepositoryUri().resolve(DEFAULT_RESOURCE_CLASS)),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                        configuration.getPublicRepositoryUri().resolve(DEFAULT_RESOURCE_CLASS).resolve(signingKeyPair.getManifestFilename())),
        };
        ResourceCertificateBuilder builder = new ResourceCertificateBuilder();
        builder.withCa(true).withEmbedded(false);
        builder.withSubjectDN(subject).withIssuerDN(subject);
        builder.withSubjectPublicKey(signingKeyPair.getPublicKey());
        builder.withSigningKeyPair(signingKeyPair);
        builder.withResources(resources);
        builder.withValidityPeriod(new ValidityPeriod(now, new DateTime(now.getYear() + 1, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC).plus(GRACEPERIOD)));
        builder.withoutAuthorityInformationAccess();
        builder.withFilename(ias.caCertificateFilename(signingKeyPair.getPublicKey()));
        builder.withParentPublicationDirectory(BASE_URI);
        builder.withSubjectInformationAccess(sia);
        builder.withSerial(dbComponent.nextSerial(ca));
        OutgoingResourceCertificate outgoing = builder.build();

        certificateManagementService.addOutgoingResourceCertificate(outgoing);
        return new CertificateIssuanceResponse(outgoing.getCertificate(), outgoing.getPublicationUri());
    }

    protected void inTx(Runnable r) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                r.run();
            }
        });
    }

    protected <T> T withTx(Supplier<T> c) {
        return transactionTemplate.execute(transactionStatus -> c.get());
    }

    protected CertificateAuthority createCaIfDoesntExist(CertificateAuthority ca) {
        final CertificateAuthority existing = certificateAuthorityRepository.find(ca.getId());
        if (existing == null) {
            certificateAuthorityRepository.add(ca);
            return certificateAuthorityRepository.find(ca.getId());
        }
        return existing;
    }
}
