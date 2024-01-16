package net.ripe.rpki.services.impl.handlers;

import com.google.common.collect.ImmutableSortedSet;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.aspa.*;
import net.ripe.rpki.domain.roa.*;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@Transactional
public class IssueUpdatedManifestAndCrlCommandHandlerTest extends CertificationDomainTestCase {

    @Inject
    private AspaConfigurationRepository aspaConfigurationRepository;
    @Inject
    private AspaEntityRepository aspaEntityRepository;
    @Inject
    private RoaConfigurationRepository roaConfigurationRepository;
    @Inject
    private RoaEntityRepository roaEntityRepository;
    @Inject
    private IssueUpdatedManifestAndCrlCommandHandler subject;
    private ManagedCertificateAuthority ca;
    private KeyPairEntity currentKeyPair;

    @Before
    public void setUp() {
        clearDatabase();
        ca = createInitialisedProdCaWithRipeResources();
        currentKeyPair = ca.getCurrentKeyPair();
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertEquals(IssueUpdatedManifestAndCrlCommand.class, subject.commandType());
    }

    @Test
    public void should_delegate_to_certificateManagementService() {
        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        assertThat(manifestEntityRepository.findByKeyPairEntity(currentKeyPair)).isNotNull();
    }

    @Test
    public void should_have_no_effect_when_update_is_not_needed() {
        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        assertThrows(
            CommandWithoutEffectException.class,
            () -> subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()))
        );
    }

    @Test
    public void should_clear_configuration_check_needed_even_if_configuration_change_had_no_effect() {
        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));
        ca.markConfigurationUpdated();

        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        assertThat(ca.isConfigurationCheckNeeded()).isFalse();
    }

    @Test
    public void should_update_roa_entities() {
        roaConfigurationRepository.getOrCreateByCertificateAuthority(ca).addPrefix(Collections.singleton(new RoaConfigurationPrefix(Asn.parse("AS3333"), IpRange.parse("10.0.0.0/8"))));
        ca.markConfigurationUpdated();

        assertThat(roaEntityRepository.findCurrentByCertificateAuthority(ca)).describedAs("current ROA entities").isEmpty();

        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        assertThat(ca.isConfigurationCheckNeeded()).isFalse();

        List<RoaEntity> roas = roaEntityRepository.findCurrentByCertificateAuthority(ca);
        assertThat(roas).describedAs("updated ROA entities").hasSize(1);

        ManifestCms updatedManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();
        PublishedObject roa = roas.get(0).getPublishedObject();
        assertThat(updatedManifest.verifyFileContents(roa.getFilename(), roa.getContent())).isTrue();
    }

    @Test
    public void should_update_aspa_entities() {
        aspaConfigurationRepository.add(new AspaConfiguration(ca, Asn.parse("AS64512"), ImmutableSortedSet.of(Asn.parse("AS1"))));
        ca.markConfigurationUpdated();

        assertThat(aspaEntityRepository.findCurrentByCertificateAuthority(ca)).describedAs("current ASPA entities").isEmpty();

        subject.handle(new IssueUpdatedManifestAndCrlCommand(ca.getVersionedId()));

        assertThat(ca.isConfigurationCheckNeeded()).isFalse();

        List<AspaEntity> aspas = aspaEntityRepository.findCurrentByCertificateAuthority(ca);
        assertThat(aspas).describedAs("updated ASPA entities").hasSize(1);

        ManifestCms updatedManifest = manifestEntityRepository.findByKeyPairEntity(currentKeyPair).getManifestCms();
        PublishedObject aspa = aspas.get(0).getPublishedObject();
        assertThat(updatedManifest.verifyFileContents(aspa.getFilename(), aspa.getContent())).isTrue();
    }
}
