package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestedResourceSetsTest {

    @Test
    public void should_certify_all_resources_when_no_subset_is_requested() {
        RequestedResourceSets subject = new RequestedResourceSets();

        assertThat(subject.calculateEffectiveResources(IpResourceSet.ALL_PRIVATE_USE_RESOURCES)).isEqualTo(IpResourceSet.ALL_PRIVATE_USE_RESOURCES);
    }

    @Test
    public void should_certify_only_requested_resources() {
        RequestedResourceSets subject = new RequestedResourceSets(
            Optional.of(IpResourceSet.parse("AS64512")),
            Optional.of(IpResourceSet.parse("10.0.0.0/8")),
            Optional.of(IpResourceSet.parse("fc00:0000::/16"))
        );

        assertThat(subject.calculateEffectiveResources(IpResourceSet.ALL_PRIVATE_USE_RESOURCES)).isEqualTo(
            IpResourceSet.parse("AS64512, 10.0.0.0/8, fc00:0000::/16")
        );
    }

    @Test
    public void should_certify_only_overlap_with_provided_certifiable_resources() {
        RequestedResourceSets subject = new RequestedResourceSets(
                Optional.of(IpResourceSet.parse("AS64512")),
                Optional.of(IpResourceSet.parse("10.0.0.0/8")),
                Optional.of(IpResourceSet.parse("fc00:0000::/16"))
        );

        assertThat(subject.calculateEffectiveResources(IpResourceSet.parse("AS64512, 2001:DB8::/32"))).isEqualTo(
                IpResourceSet.parse("AS64512")
        );
    }
}
