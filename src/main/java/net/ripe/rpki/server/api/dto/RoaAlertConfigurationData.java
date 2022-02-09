package net.ripe.rpki.server.api.dto;

import lombok.ToString;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.*;

@ToString
public class RoaAlertConfigurationData extends ValueObjectSupport {

    private static final long serialVersionUID = 1L;

    private final CertificateAuthorityData certificateAuthority;
    private final RoaAlertSubscriptionData subscription;
    private final Set<AnnouncedRoute> ignoredAnnouncements;

    public RoaAlertConfigurationData(CertificateAuthorityData certificateAuthority, RoaAlertSubscriptionData subscription) {
        this(certificateAuthority, subscription, Collections.emptySet());
    }

    public RoaAlertConfigurationData(CertificateAuthorityData certificateAuthority, RoaAlertSubscriptionData subscription, Collection<AnnouncedRoute> ignoredAnnouncements) {
        this.certificateAuthority = certificateAuthority;
        this.subscription = subscription;
        this.ignoredAnnouncements = new HashSet<>(ignoredAnnouncements);
    }

    public CertificateAuthorityData getCertificateAuthority() {
        return certificateAuthority;
    }

    public RoaAlertSubscriptionData getSubscription() {
        return subscription;
    }

    public Set<AnnouncedRoute> getIgnoredAnnouncements() {
        return ignoredAnnouncements;
    }
    
    public List<String> getEmails() {
        return subscription == null ? Collections.emptyList() : subscription.getEmails();
    }

    public Set<RouteValidityState> getRouteValidityStates() {
        return subscription == null ? Collections.emptySet() : subscription.getRouteValidityStates();
    }

    public boolean hasSubscription() {
        return subscription != null;
    }

    public RoaAlertConfigurationData withIgnoredAnnouncements(Set<AnnouncedRoute> ignoredAnnouncements) {
        return new RoaAlertConfigurationData(certificateAuthority, subscription, ignoredAnnouncements);
    }
}
