package net.ripe.rpki.server.api.dto;

import lombok.Getter;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.util.*;

@Getter
public class RoaAlertSubscriptionData extends ValueObjectSupport {

    private final List<String> emails;
    private final RoaAlertFrequency frequency;
    private final EnumSet<RouteValidityState> routeValidityStates;

    public RoaAlertSubscriptionData(String email, Collection<RouteValidityState> routeValidityStates, RoaAlertFrequency frequency) {
        this(List.of(email), routeValidityStates, frequency);
    }

    public RoaAlertSubscriptionData(List<String> emails, Collection<RouteValidityState> routeValidityStates,
                                    RoaAlertFrequency frequency) {
        this.emails = new ArrayList<>(emails);
        this.routeValidityStates = EnumSet.copyOf(routeValidityStates);
        this.frequency = frequency;
    }
}
