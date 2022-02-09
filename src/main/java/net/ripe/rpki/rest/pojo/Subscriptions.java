package net.ripe.rpki.rest.pojo;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.ripe.rpki.domain.alerts.RoaAlertFrequency;

public class Subscriptions {
    private Set<String> emails;
    private Set<String> routeValidityStates;
    private RoaAlertFrequency frequency;

    public Subscriptions() {
    }

    public Subscriptions(Set<String> emails, Set<String> routeValidityStates, RoaAlertFrequency frequency) {
        this.routeValidityStates = routeValidityStates;
        this.emails = emails;
        this.frequency = frequency;
    }

    public Subscriptions(Set<String> emails, Set<String> routeValidityStates) {
        this(emails, routeValidityStates, RoaAlertFrequency.DAILY);
    }

    public Set<String> getRouteValidityStates() {
        return routeValidityStates == null ?
                Collections.emptySet() :
                routeValidityStates.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public void setRouteValidityStates(Set<String> routeValidityStates) {
        this.routeValidityStates = routeValidityStates;
    }

    public Set<String> getEmails() {
        return emails == null ?
                Collections.emptySet() :
                emails.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public void setEmails(Set<String> emails) {
        this.emails = emails;
    }

    public RoaAlertFrequency getFrequency() {
        return frequency;
    }

    @Override
    public String toString() {
        return "Subscriptions{" +
                "emails=" + emails +
                ", routeValidityStates=" + routeValidityStates +
                ", frequency=" + frequency +
                '}';
    }
}
