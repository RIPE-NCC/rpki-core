package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.*;


public class RoaAlertSubscriptionData extends ValueObjectSupport {

    private final List<String> emails;
    private final EnumSet<RouteValidityState> routeValidityStates;
    private final RoaAlertFrequency frequency;

    public RoaAlertSubscriptionData(String email, Collection<RouteValidityState> routeValidityStates, RoaAlertFrequency frequency) {
        this.emails = new ArrayList<>();
        emails.add(email);
        this.routeValidityStates = EnumSet.copyOf(routeValidityStates);
        this.frequency = frequency;
    }

    public RoaAlertSubscriptionData(List<String> emails, Collection<RouteValidityState> routeValidityStates, RoaAlertFrequency frequency) {
        this.emails = new ArrayList<>(emails);
        this.routeValidityStates = EnumSet.copyOf(routeValidityStates);
        this.frequency = frequency;
    }

    public List<String> getEmails() {
        return emails;
    }

    public RoaAlertFrequency getFrequency() {
        return frequency;
    }

    public Set<RouteValidityState> getRouteValidityStates() {
        return routeValidityStates;
    }
}
