package net.ripe.rpki.services.impl;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityTest;
import net.ripe.rpki.server.api.dto.RoaEntityData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.NoResultException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoaServiceBeanTest {

    private static final Long TEST_CA_ID = 2L;

    private ManagedCertificateAuthority certificateAuthority;
    private CertificateAuthorityRepository caRepository;
    private RoaConfigurationRepository roaConfigurationRepository;
    private RoaEntityRepository roaEntityRepository;
    private RoaServiceBean subject;


    @Before
    public void setUp() {
        certificateAuthority = TestObjects.createInitialisedProdCaWithRipeResources();
        caRepository = mock(CertificateAuthorityRepository.class);
        roaConfigurationRepository = mock(RoaConfigurationRepository.class);
        roaEntityRepository = mock(RoaEntityRepository.class);
        subject = new RoaServiceBean(caRepository, roaConfigurationRepository, roaEntityRepository);
    }

    @Test
    public void shouldFindAllRoasForCa() {
        DateTime now = new DateTime(DateTimeZone.UTC);
        RoaEntity roa1 = RoaEntityTest.createEeSignedRoaEntity(certificateAuthority,
            certificateAuthority.getCurrentKeyPair().getPublicKey(), new ValidityPeriod(now, now.plusYears(1)));
        RoaEntity roa2 = RoaEntityTest.createEeSignedRoaEntity(certificateAuthority,
            certificateAuthority.getCurrentKeyPair().getPublicKey(), new ValidityPeriod(now, now.plusYears(1)));
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(roaEntityRepository.findByCertificateSigningKeyPair(isA(KeyPairEntity.class))).thenReturn(Arrays.asList(roa1, roa2));

        List<RoaEntityData> result = subject.findAllRoasForCa(TEST_CA_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRoaCms()).isEqualTo(roa1.getRoaCms());
        assertThat(result.get(1).getRoaCms()).isEqualTo(roa2.getRoaCms());
    }

    @Test
    public void getRoaConfiguration_should_throw_NoResultException_when_ca_is_not_found() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(null);

        assertThatThrownBy(() -> subject.getRoaConfiguration(TEST_CA_ID)).isInstanceOf(NoResultException.class);
    }

    @Test
    public void getRoaConfiguration_should_default_to_empty_configuration() {
        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(roaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(Optional.empty());

        assertThat(subject.getRoaConfiguration(TEST_CA_ID)).isEqualTo(new RoaConfiguration(certificateAuthority).convertToData());
    }

    @Test
    public void getRoaConfiguration_should_return_ca_roa_configuration() {
        RoaConfiguration roaConfiguration = new RoaConfiguration(certificateAuthority);
        roaConfiguration.addPrefix(Collections.singleton(new RoaConfigurationPrefix(Asn.parse("AS3333"), IpRange.parse("127.0.0.0/8"))));

        when(caRepository.findManagedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(roaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(Optional.of(roaConfiguration));

        assertThat(subject.getRoaConfiguration(TEST_CA_ID)).isEqualTo(roaConfiguration.convertToData());
    }
}
