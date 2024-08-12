package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.domain.roa.RoaConfiguration.ROA_CONFIGURATION_PREFIX_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RoaConfigurationTest {
    static final Asn DOCUMENTATION_AS_1 = Asn.parse("AS64396");
    static final Asn DOCUMENTATION_AS_2 = Asn.parse("AS64397");
    static final IpRange TEST_NET_1 = IpRange.parse("192.0.2.0/24");
    static final IpRange TEST_NET_2 = IpRange.parse("198.51.100.0/24");

    // We re-use the same instant for multiple objects
    private final static Instant THEN = Instant.now();

    // recall: The effective maximum length is the prefix length by default - specifying null is identical to a value
    private static final RoaConfigurationPrefix AS64396_TEST_NET_1_24 = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_1, null, THEN);
    private static final RoaConfigurationPrefix AS64396_TEST_NET_1_32 = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_1, 32, THEN);
    private static final RoaConfigurationPrefix AS64396_TEST_NET_2_24 = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_2, null, THEN);

    private static final RoaConfigurationPrefix AS64397_TEST_NET_1_24 = new RoaConfigurationPrefix(DOCUMENTATION_AS_2, TEST_NET_1, null, THEN);

    private ManagedCertificateAuthority certificateAuthority;
    private RoaConfiguration subject;

    @BeforeEach
    void setUp() {
        certificateAuthority = mock(ManagedCertificateAuthority.class);
        subject = new RoaConfiguration(certificateAuthority, Collections.emptyList());
    }

    @Test
    void should_add_roa_prefix() {
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));

        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_24);

        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_2_24));
        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_24, AS64396_TEST_NET_2_24);
    }

    @Test
    void should_add_multiple() {
        subject.addPrefixes(List.of(AS64396_TEST_NET_1_32, AS64396_TEST_NET_2_24));
        assertThat(subject.getPrefixes()).containsExactlyInAnyOrder(AS64396_TEST_NET_1_32, AS64396_TEST_NET_2_24);
    }

    @Test
    void should_replace_roa_prefix_when_only_maximum_length_changed() {
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_32));

        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_32);
    }

    @Test
    void should_retain_most_specific_on_conflict() {
        // later wins
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_32));

        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_32);
        // more specific does not get overwritten
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));

        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_32);
    }

    @Test
    void should_retain_first_on_conflict() {
        var laterConflict = new RoaConfigurationPrefix(AS64396_TEST_NET_1_32.getAsn(), AS64396_TEST_NET_1_32.getPrefix(), AS64396_TEST_NET_1_32.getMaximumLength(), Instant.now().plusSeconds(3600));

        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_32));
        subject.addPrefixes(Collections.singleton(laterConflict));

        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_32);
    }

    @Test
    void should_remove_roa_prefix() {
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));
        assertThat(subject.getPrefixes()).isNotEmpty();
        subject.removePrefixes(Collections.singleton(AS64396_TEST_NET_1_24));

        assertThat(subject.getPrefixes()).isEmpty();
    }

    @Test
    void should_replace_with_more_specific() {
        subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_24));
        var diff = subject.mergePrefixes(Collections.singleton(AS64396_TEST_NET_1_32), Collections.singleton(AS64396_TEST_NET_1_24));
        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_32);
        assertThat(diff.removed()).containsOnly(AS64396_TEST_NET_1_24);
        assertThat(diff.added()).containsOnly(AS64396_TEST_NET_1_32);
    }

    @Test
    void should_replace_with_less_specific() {
        var diff1 = subject.addPrefixes(Collections.singleton(AS64396_TEST_NET_1_32));
        assertThat(diff1.added()).containsOnly(AS64396_TEST_NET_1_32);
        assertThat(diff1.removed()).isEmpty();
        var diff = subject.mergePrefixes(Collections.singleton(AS64396_TEST_NET_1_24), Collections.singleton(AS64396_TEST_NET_1_32));
        assertThat(subject.getPrefixes()).containsOnly(AS64396_TEST_NET_1_24);
        assertThat(diff.removed()).containsOnly(AS64396_TEST_NET_1_32);
        assertThat(diff.added()).containsOnly(AS64396_TEST_NET_1_24);
    }

    @Test
    void comparator_should_sort() {
        // Get two equal objects, even equal time.
        var as64396TestNet1_24_Explicit = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_1, 24, THEN);
        assertThat(ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(as64396TestNet1_24_Explicit, AS64396_TEST_NET_1_24)).isZero();

        // The new object is before the other -> should be before
        var as64396TestNet1_24_before = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_1, null, THEN.minusSeconds(3600));
        assertThat(ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(AS64396_TEST_NET_1_24, as64396TestNet1_24_before)).isPositive();

        // The new object has null time -> should be after
        var as64396TestNet1_24_null = new RoaConfigurationPrefix(DOCUMENTATION_AS_1, TEST_NET_1, null, null);
        assertThat(ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(AS64396_TEST_NET_1_24, as64396TestNet1_24_null)).isNegative();

        // Regular cases:
        assertThat(ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(AS64396_TEST_NET_1_24, AS64396_TEST_NET_1_32)).isPositive();
        assertThat(ROA_CONFIGURATION_PREFIX_COMPARATOR.compare(AS64396_TEST_NET_1_24, AS64397_TEST_NET_1_24)).isNegative();

    }
}
