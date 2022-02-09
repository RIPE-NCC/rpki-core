package net.ripe.rpki.application.impl;

import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceCertificateInformationAccessStrategyBeanTest {

    @Test
    public void shouldUseSafeCharactersForSubject() {
        ResourceCertificateInformationAccessStrategyBean subject = new ResourceCertificateInformationAccessStrategyBean();
        X500Principal caCertificateSubject = subject.caCertificateSubject(TestObjects.TEST_KEY_PAIR_2.getPublicKey());

        Pattern expected = Pattern.compile("CN=[\\d\\w]+");
        assertTrue(expected.matcher(caCertificateSubject.toString()).matches());
    }

    @Test
    public void shouldUseUuidInDefaultRepositoryLocationForChildCa() {
        ResourceCertificateInformationAccessStrategyBean subject = new ResourceCertificateInformationAccessStrategyBean();
        CustomerCertificateAuthority memberCa = mock(CustomerCertificateAuthority.class);
        when(memberCa.isProductionCa()).thenReturn(false);

        UUID memberCaUuid = UUID.fromString("6d7ac25d-6e33-450d-a860-36d42c699c4f");
        when(memberCa.getUuid()).thenReturn(memberCaUuid);

        URI expectedLocation = URI.create("TESTRC/6d/7ac25d-6e33-450d-a860-36d42c699c4f/1/");

        assertEquals(expectedLocation, subject.defaultCertificateRepositoryLocation(memberCa, "TESTRC"));
    }

    @Test
    public void shouldUseResourceClassBaseForDefaultRepositoryLocationForProductionCa() {
        ResourceCertificateInformationAccessStrategyBean subject = new ResourceCertificateInformationAccessStrategyBean();
        ProductionCertificateAuthority prodCa = mock(ProductionCertificateAuthority.class);
        when(prodCa.isProductionCa()).thenReturn(true);

        URI expectedLocation = URI.create("TESTRC/");

        assertEquals(expectedLocation, subject.defaultCertificateRepositoryLocation(prodCa, "TESTRC"));
    }

    @Test
    public void shouldUseResourceClassBaseForDefaultRepositoryLocationForAllResourcesCa() {
        ResourceCertificateInformationAccessStrategyBean subject = new ResourceCertificateInformationAccessStrategyBean();
        AllResourcesCertificateAuthority aCa = mock(AllResourcesCertificateAuthority.class);
        when(aCa.isAllResourcesCa()).thenReturn(true);

        URI expectedLocation = URI.create("aca/");

        assertEquals(expectedLocation, subject.defaultCertificateRepositoryLocation(aCa, ResourceCertificateInformationAccessStrategyBean.ACA_PUBLICATION_SUBDIRECTORY));
    }

}
