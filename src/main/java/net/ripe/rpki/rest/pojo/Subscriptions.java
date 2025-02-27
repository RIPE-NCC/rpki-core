package net.ripe.rpki.rest.pojo;

import com.google.common.collect.Sets;
import lombok.*;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Subscriptions {
    @Setter
    private Set<String> emails;
    @Setter
    private Set<String> routeValidityStates;
    @Getter
    private RoaAlertFrequency frequency;
    @Getter
    @Setter
    private boolean notifyOnRoaChanges = false;

    public Subscriptions(Set<String> emails, Set<String> routeValidityStates) {
        this(emails, routeValidityStates, RoaAlertFrequency.DAILY, false);
    }

    public Set<String> getRouteValidityStates() {
        return routeValidityStates == null ?
                Collections.emptySet() :
                routeValidityStates.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public Set<String> getEmails() {
        return emails == null ?
                Collections.emptySet() :
                emails.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public static Subscriptions defaultSubscriptions(Collection<String> emails, Set<String> validityStates) {
        return new Subscriptions(Sets.newHashSet(emails), validityStates);
    }

    public static Subscriptions defaultSubscriptions() {
        return new Subscriptions(Collections.emptySet(), Collections.emptySet());
    }
}
