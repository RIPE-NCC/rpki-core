package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.CertificateInformationAccessUtil;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityService;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.MigrateMemberCertificateAuthorityToIntermediateParentCommand;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.Map;

import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_RESOURCES;
import static org.assertj.core.api.Assertions.assertThat;

public class MigrateMemberCertificateAuthorityToIntermediateParentCommandHandlerTest extends CertificationDomainTestCase {

    @Inject
    private MigrateMemberCertificateAuthorityToIntermediateParentCommandHandler subject;

    @Inject
    private RoaEntityRepository roaEntityRepository;

    @Inject
    private RoaEntityService roaEntityService;

    @Inject
    private ResourceCache resourceCache;

    @Before
    public void setUp() {
        transactionTemplate.executeWithoutResult((status) -> clearDatabase());
    }

    @Test
    @Transactional
    public void should_revoke_old_certificate_and_request_new_certificate() {
        VersionedId intermediateCaId = commandService.getNextId();
        X500Principal intermediateCaName = new X500Principal("CN=intermediate");
        X500Principal childCaName = new X500Principal("CN=hosted");
        ImmutableResourceSet childCaResources = ImmutableResourceSet.parse("10.0.0.0/24");
        resourceCache.populateCache(Map.ofEntries(
            Map.entry(CaName.of(PRODUCTION_CA_NAME), PRODUCTION_CA_RESOURCES),
            Map.entry(CaName.of(childCaName), childCaResources)
        ));

        ProductionCertificateAuthority productionCa = createInitialisedProdCaWithRipeResources();
        commandService.execute(new CreateIntermediateCertificateAuthorityCommand(intermediateCaId, intermediateCaName, productionCa.getId()));
        IntermediateCertificateAuthority intermediateCa = certificateAuthorityRepository.findByTypeAndName(IntermediateCertificateAuthority.class, intermediateCaName);
        commandService.execute(new ActivateHostedCertificateAuthorityCommand(commandService.getNextId(), childCaName, childCaResources, productionCa.getId()));
        HostedCertificateAuthority childCa = certificateAuthorityRepository.findByTypeAndName(HostedCertificateAuthority.class, childCaName);

        assertThat(childCa.getParent()).isEqualTo(productionCa);
        // Child CA's certificate was signed by production CA.
        OutgoingResourceCertificate childCaSignedByProductionCaCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(
            childCa.getCurrentIncomingCertificate().getSubjectPublicKey(),
            productionCa.getCurrentKeyPair()
        );
        assertThat(childCaSignedByProductionCaCertificate.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT);
        assertThat(childCa.findCurrentIncomingResourceCertificate()).hasValueSatisfying(irc -> {
            URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(productionCa.getCurrentIncomingCertificate().getSia());
            assertThat(irc.getPublicationUri().toString()).startsWith(publicationDirectory.toString());
            assertThat(irc.getCertificate().getParentCertificateUri()).isEqualTo(productionCa.getCurrentIncomingCertificate().getPublicationUri());
        });

        // Change the parent of the child CA to the intermediate CA.
        commandService.execute(new MigrateMemberCertificateAuthorityToIntermediateParentCommand(childCa.getVersionedId(), intermediateCaId.getId()));

        assertThat(childCa.getParent()).isEqualTo(intermediateCa);
        // Child CA's certificate is now signed by intermediate CA and old certificate signed by production CA is revoked.
        OutgoingResourceCertificate childCaSignedByIntermediateCaCertificate = resourceCertificateRepository.findLatestOutgoingCertificate(
            childCa.getCurrentIncomingCertificate().getSubjectPublicKey(),
            intermediateCa.getCurrentKeyPair()
        );
        assertThat(childCaSignedByProductionCaCertificate.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.REVOKED);
        assertThat(childCaSignedByIntermediateCaCertificate.getStatus()).isEqualTo(OutgoingResourceCertificateStatus.CURRENT);
        assertThat(childCa.findCurrentIncomingResourceCertificate()).hasValueSatisfying(irc -> {
            URI publicationDirectory = CertificateInformationAccessUtil.extractPublicationDirectory(intermediateCa.getCurrentIncomingCertificate().getSia());
            assertThat(irc.getPublicationUri().toString()).startsWith(publicationDirectory.toString());
            assertThat(irc.getCertificate().getParentCertificateUri()).isEqualTo(intermediateCa.getCurrentIncomingCertificate().getPublicationUri());
        });
    }
}
