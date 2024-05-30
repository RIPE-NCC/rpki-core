package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.Roa;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoaSpecificationTest {

    private static final DateTime NOW = new DateTime(DateTimeZone.UTC);

    private static final Asn ASN = Asn.parse("AS123");

    private static final ValidityPeriod VALIDITY_PERIOD = new ValidityPeriod(NOW.toInstant(), NOW.plusYears(1));

    public static final IpRange RESOURCE_1 = IpRange.parse("10.0.0.0/8");
    public static final AllowedRoute ROA_PREFIX_1 = new AllowedRoute(ASN, RESOURCE_1, 16);

    public static final IpRange RESOURCE_2 = IpRange.parse("192.168.0.0/24");
    public static final AllowedRoute ROA_PREFIX_2 = new AllowedRoute(ASN, RESOURCE_2, RESOURCE_2.getPrefixLength());

    private RoaSpecification subject;
    private RoaStub roa;
    private ValidityPeriod caCertValidityPeriod;

    private IncomingResourceCertificate incomingCertificate;

    private ImmutableResourceSet resources;


    public static RoaSpecification createRoaSpecification() {
        RoaSpecification result = new RoaSpecification(ASN, VALIDITY_PERIOD);
        result.addAllowedRoute(ROA_PREFIX_1);
        result.addAllowedRoute(ROA_PREFIX_2);
        return result;
    }

    @Before
    public void setUp() {
        caCertValidityPeriod = new ValidityPeriod(NOW, NOW.plusYears(2));
        incomingCertificate = mock(IncomingResourceCertificate.class);

        resources = ImmutableResourceSet.of(RESOURCE_1, RESOURCE_2);

        when(incomingCertificate.getValidityPeriod()).thenReturn(caCertValidityPeriod);
        when(incomingCertificate.getResources()).thenReturn(resources);

        subject = createRoaSpecification();
        roa = new RoaStub(subject.calculateValidityPeriod(), subject.getAsn(), asList(ROA_PREFIX_1.getRoaPrefix(), ROA_PREFIX_2.getRoaPrefix()));
    }

    @Test
    public void should_calculate_resources_based_on_prefixes() {
        assertEquals(resources, subject.getNormalisedResources());
        subject.removeAllowedRoute(ROA_PREFIX_1);
        assertEquals(ImmutableResourceSet.of(RESOURCE_2), subject.getNormalisedResources());
    }

    @Test
    public void should_update_maximum_length_when_adding_allowed_route_with_same_prefix() {
        subject.addAllowedRoute(new AllowedRoute(ASN, RESOURCE_1, 8));
        assertEquals(8, subject.getPrefix(RESOURCE_1));
    }


    @Test
    public void should_be_satisfied_by_exact_roa_match() {
        assertTrue(subject.isSatisfiedBy(roa));
    }

    @Test
    public void should_not_be_satisfied_by_roa_with_less_prefixes() {
        RoaSpecification subject = new RoaSpecification(ASN, VALIDITY_PERIOD);
        subject.addAllowedRoute(ROA_PREFIX_1);
        subject.addAllowedRoute(ROA_PREFIX_2);

        RoaStub roa = new RoaStub(subject.calculateValidityPeriod(), subject.getAsn(), asList(ROA_PREFIX_1.getRoaPrefix()));

        assertFalse(subject.isSatisfiedBy(roa));
    }

    @Test
    public void should_not_be_satisfied_by_roa_with_more_prefixes() {
        RoaSpecification subject = new RoaSpecification(ASN, VALIDITY_PERIOD);
        subject.addAllowedRoute(ROA_PREFIX_1);

        RoaStub roa = new RoaStub(subject.calculateValidityPeriod(), subject.getAsn(), asList(ROA_PREFIX_1.getRoaPrefix(), ROA_PREFIX_2.getRoaPrefix()));

        assertFalse(subject.isSatisfiedBy(roa));
    }

    @Test
    public void should_not_be_satisfied_by_roa_with_not_valid_before_in_the_future() {
        roa.setValidityPeriod(roa.getValidityPeriod().withNotValidBefore(NOW.plusDays(2)));
        assertFalse(subject.isSatisfiedBy(roa));
    }

    @Test
    public void should_not_be_satisfied_by_roa_with_different_not_valid_after() {
        roa.setValidityPeriod(roa.getValidityPeriod().withNotValidAfter(NOW.plusYears(10)));
        assertFalse(subject.isSatisfiedBy(roa));
    }

    @Test
    public void should_allow_roa_with_fewer_prefixes() {
        roa.setPrefixes(Collections.singletonList(ROA_PREFIX_1.getRoaPrefix()));
        assertTrue(subject.allows(roa));
    }

    @Test
    public void should_prohibit_roa_with_more_prefixes() {
        roa.setPrefixes(asList(ROA_PREFIX_1.getRoaPrefix(), ROA_PREFIX_2.getRoaPrefix(), new RoaPrefix(IpRange.parse("172.16.0.0/12"), null)));
        assertFalse(subject.allows(roa));
    }

    @Test
    public void should_allow_roa_with_longer_prefixes() {
        roa.setPrefixes(asList(new RoaPrefix(IpRange.parse("10.0.0.0/16"), 16), ROA_PREFIX_2.getRoaPrefix()));
        assertTrue(subject.allows(roa));
    }

    @Test
    public void should_prohibit_roa_with_shorter_prefixes() {
        roa.setPrefixes(asList(new RoaPrefix(RESOURCE_1, null), new RoaPrefix(IpRange.parse("192.168.0.0/16"), null)));
        assertFalse(subject.allows(roa));
    }

    @Test
    public void should_prohibit_roa_with_different_maximum_prefix_length() {
        roa.setPrefixes(asList(new RoaPrefix(IpRange.parse("10.0.0.0/8"), 15), new RoaPrefix(RESOURCE_2, null)));
        assertFalse(subject.allows(roa));
    }

    @Test
    public void should_allow_roa_with_shorter_validity_period() {
        ValidityPeriod vp = roa.getValidityPeriod();
        vp = vp.withNotValidAfter(vp.getNotValidAfter().minusDays(1));
        roa.setValidityPeriod(vp);
        assertTrue(subject.allows(roa));
    }

    @Test
    public void should_prohibit_roa_with_different_asn() {
        roa.setAsn(Asn.parse("AS999"));
        assertFalse(subject.allows(roa));
    }

    @Test
    public void should_allow_roa_with_validity_date_in_the_future() {
        ValidityPeriod vp = roa.getValidityPeriod();
        vp = vp.withNotValidBefore(vp.getNotValidBefore().plusDays(1));
        roa.setValidityPeriod(vp);
        assertTrue(subject.allows(roa));
    }



    private static final class RoaStub implements Roa {

        private ValidityPeriod validityPeriod;
        private Asn asn;
        private List<RoaPrefix> prefixes;


        public RoaStub(ValidityPeriod validityPeriod, Asn asn, List<RoaPrefix> prefixes) {
            this.validityPeriod = validityPeriod;
            this.asn = asn;
            this.prefixes = prefixes;
        }

        @Override
        public Asn getAsn() {
            return asn;
        }

        public void setAsn(Asn asn) {
            this.asn = asn;
        }

        @Override
        public List<RoaPrefix> getPrefixes() {
            return prefixes;
        }

        public void setPrefixes(List<RoaPrefix> prefixes) {
            this.prefixes = prefixes;
        }

        @Override
        public ValidityPeriod getValidityPeriod() {
            return validityPeriod;
        }

        public void setValidityPeriod(ValidityPeriod validityPeriod) {
            this.validityPeriod = validityPeriod;
        }
    }
}
