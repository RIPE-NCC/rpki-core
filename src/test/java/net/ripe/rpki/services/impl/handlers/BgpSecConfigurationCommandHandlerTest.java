package net.ripe.rpki.services.impl.handlers;

import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import net.ripe.rpki.domain.bgpsec.BgpSecConfigurationRepository;
import net.ripe.rpki.domain.bgpsec.RouterId;
import net.ripe.rpki.server.api.commands.CreateBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.commands.DeleteBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Rollback
public class BgpSecConfigurationCommandHandlerTest extends CertificationDomainTestCase {

    // This one is copied from
    // https://krill.docs.nlnetlabs.nl/en/stable/cli.html#krillc-bgpsec
    public static final String CSR = readCsr("bgpsec/csr1.der");
    public static final String CSR2 = readCsr("bgpsec/csr2.der");

    @SneakyThrows
    private static String readCsr(String name) {
        final byte[] csr = Files.readAllBytes(Paths.get(
                Objects.requireNonNull(BgpSecConfigurationCommandHandlerTest.class.getClassLoader().getResource(name)).toURI()));
        return Base64.getEncoder().encodeToString(csr);
    }

    private ManagedCertificateAuthority certificateAuthority;

    private AddBgpSecConfigurationCommandHandler addHandler;
    private DeleteBgpSecConfigurationCommandHandler removeHandler;

    @Autowired
    private BgpSecConfigurationRepository bgpSecConfigurationRepository;

    @Before
    public void setUp() {
        clearDatabase();
        certificateAuthority = createInitialisedProdCaWithRipeResources();
        addHandler = new AddBgpSecConfigurationCommandHandler(certificateAuthorityRepository, bgpSecConfigurationRepository);
        removeHandler = new DeleteBgpSecConfigurationCommandHandler(certificateAuthorityRepository, bgpSecConfigurationRepository);
    }

    @Test
    public void add_should_have_no_effect_when_already_present() {
        var createCommand = new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(), new Asn(64512), new RouterId(0L), CSR);
        addHandler.handle(createCommand);

        assertThatThrownBy(() -> {
            addHandler.handle(createCommand);
        }).isInstanceOf(CommandWithoutEffectException.class);
    }

    @Test
    public void remove_should_have_no_effect_when_not_present() {
        var missing = BgpSecConfigurationData.from(42L, new Asn(64512), new RouterId(0L), CSR);
        var deleteCommand = new DeleteBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                missing);
        assertThatThrownBy(() -> {
            removeHandler.handle(deleteCommand);
        }).isInstanceOf(CommandWithoutEffectException.class);
    }

    @Test
    public void should_add_new_configuration() {
        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                new Asn(64512), new RouterId(0L), CSR)
        );
        assertThat(bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority)).hasSize(1);
    }

    @Test
    public void should_remove_configuration() {
        should_add_new_configuration();

        var bgpSecs = bgpSecConfigurationRepository
                .findByCertificateAuthority(certificateAuthority)
                .stream()
                .map(BgpSecConfiguration::toData)
                .toList();

        removeHandler.handle(new DeleteBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                bgpSecs.getFirst()));

        assertThat(bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority)).isEmpty();
    }

    @Test
    public void should_add_two_and_remove_one() {
        var asn = new Asn(64512);

        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                asn, new RouterId(0L), CSR));

        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                asn, new RouterId(10L), CSR));

        var bgpsecConfigurations = bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority);
        assertThat(bgpsecConfigurations).hasSize(2);

        removeHandler.handle(new DeleteBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                BgpSecConfigurationData.from(bgpsecConfigurations.getFirst().getId(), asn, new RouterId(0L), CSR)));

        assertThat(bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority)).hasSize(1);
    }

    @Test
    public void should_add_three_remove_two() {
        var asn = new Asn(64512);
        var csr = "MIIBVDCB+wIBADBzMQswCQYDVQQGEwJJRTETMBEGA1UECAwKUG9ydCBTdXNhbjEaMBgGA1UEBwwRTm9ydGggSm9zZXBoYnVyZ2gxFTATBgNVBAoMDENoZW4gTHRkIEx0ZDEcMBoGA1UEAwwTbWF0dGhld3MtbW9ycmlzLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABJlQbNOgsPdcLUQ/b14/5D7M5+gIiGZsd0RqbhW1nr2HxYmt7rJNXATvlSBMUJKl/KYzQH8LENccwyqW9FGt6A2gJjAkBgkqhkiG9w0BCQ4xFzAVMBMGA1UdEQQMMAqCCGtpbmcuY29tMAoGCCqGSM49BAMCA0gAMEUCIQD2FiPVgQYP8O8U/Ot8yxtuA0de//U6Utwmmu8fmRZqLQIgPj9SSWIe/T1H70OmgXlPynR/gLgZsJh4Cuqr+EgKRWI=";

        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                asn, new RouterId(739672219L), csr));

        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                asn, new RouterId(26721763L), csr));

        addHandler.handle(new CreateBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                asn, new RouterId(345649748L), csr));

        var bgpsecConfigurations = bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority);
        assertThat(bgpsecConfigurations).hasSize(3);

        removeHandler.handle(new DeleteBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                BgpSecConfigurationData.from(bgpsecConfigurations.getFirst().getId(), asn, new RouterId(739672219L), csr)));

        removeHandler.handle(new DeleteBgpSecConfigurationCommand(
                certificateAuthority.getVersionedId(),
                BgpSecConfigurationData.from(bgpsecConfigurations.get(1).getId(), asn, new RouterId(345649748L), csr)));

        assertThat(bgpSecConfigurationRepository.findByCertificateAuthority(certificateAuthority)).hasSize(1);
    }
}
