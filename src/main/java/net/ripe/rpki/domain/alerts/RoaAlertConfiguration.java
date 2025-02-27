package net.ripe.rpki.domain.alerts;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name="roa_alert_configuration")
@SequenceGenerator(name = "roa_alert_conf_seq", sequenceName = "seq_all", allocationSize=1)
public class RoaAlertConfiguration extends EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roa_alert_conf_seq")
    @Getter
    private Long id;

    @Getter
    @OneToOne(optional=false, fetch=FetchType.EAGER)
    @JoinColumn(name = "certificateauthority_id")
    private CertificateAuthority certificateAuthority;

    @Basic(optional=false)
    private String email; // May be empty for no subscription.

    @Getter
    @Basic(optional=false)
    @Column(name = "frequency")
    @Enumerated(EnumType.STRING)
    private RoaAlertFrequency frequency;

    @Basic(optional=false)
    @Column(name = "route_validity_states")
    private String routeValidityStates;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "roa_alert_configuration_ignored", joinColumns = @JoinColumn(name = "roa_alert_configuration_id"))
    private Set<RoaAlertIgnoredAnnouncement> ignored = new HashSet<>();

    @Basic(optional=false)
    @Getter
    @Setter
    private boolean notifyOnRoaChanges;

    private static final String EMAIL_SEPARATOR = ",";

    public RoaAlertConfiguration() {
    }

    public RoaAlertConfiguration(CertificateAuthority certificateAuthority) {
        Validate.notNull(certificateAuthority, "certificateAuthority is required");
        this.certificateAuthority = certificateAuthority;
    }

    public RoaAlertConfiguration(CertificateAuthority certificateAuthority, String email,
                                 Collection<RouteValidityState> routeValidityStates, RoaAlertFrequency frequency) {
        this(certificateAuthority);
        setSubscription(new RoaAlertSubscriptionData(List.of(email), routeValidityStates, frequency, false));
    }

    public void clearSubscription() {
        email = "";
        routeValidityStates = "";
    }

    public void setSubscription(RoaAlertSubscriptionData subscription) {
        Validate.notEmpty(subscription.getEmails(), "emails are required");
        Validate.notEmpty(subscription.getRouteValidityStates(), "route validity state is required");
        addEmails(subscription);
        routeValidityStates = StringUtils.join(subscription.getRouteValidityStates(), ",");
        frequency = subscription.getFrequency();
        notifyOnRoaChanges = subscription.isNotifyOnRoaChanges();
    }

    private void addEmails(RoaAlertSubscriptionData subscription) {
        if (email == null || email.isEmpty()) {
            email = StringUtils.join(normEmail(subscription.getEmails()), EMAIL_SEPARATOR);
        } else {
            final Set<String> emails = Sets.newHashSet(email.split(EMAIL_SEPARATOR));
            emails.addAll(normEmail(subscription.getEmails()));
            email = StringUtils.join(emails, EMAIL_SEPARATOR);
        }
    }

    public void removeEmail(String e) {
        if (email != null && !email.isEmpty()) {
            final Set<String> split = Sets.newHashSet(email.split(EMAIL_SEPARATOR));
            split.remove(normEmail(e));
            email = StringUtils.join(split, EMAIL_SEPARATOR);
        }
    }

    public RoaAlertSubscriptionData getSubscriptionOrNull() {
        if (email.isEmpty()) {
            return null;
        }
        return new RoaAlertSubscriptionData(Arrays.asList(email.split(",")), getRouteValidityStates(), frequency, notifyOnRoaChanges);
    }

    public Set<RoaAlertIgnoredAnnouncement> getIgnored() {
        return Collections.unmodifiableSet(ignored);
    }

    public void update(Collection<AnnouncedRoute> additions, Collection<AnnouncedRoute> deletions) {
        for (AnnouncedRoute addition : additions) {
            ignored.add(new RoaAlertIgnoredAnnouncement(addition));
        }
        for (AnnouncedRoute deletion : deletions) {
            ignored.remove(new RoaAlertIgnoredAnnouncement(deletion));
        }
    }

    public RoaAlertConfigurationData toData() {
        Set<AnnouncedRoute> ignoredAnnouncements = new HashSet<>(ignored.size());
        for (RoaAlertIgnoredAnnouncement roaAlertIgnoredAnnouncement : ignored) {
            ignoredAnnouncements.add(roaAlertIgnoredAnnouncement.toData());
        }
        return new RoaAlertConfigurationData(certificateAuthority.toData(), getSubscriptionOrNull(), ignoredAnnouncements);
    }

    private EnumSet<RouteValidityState> getRouteValidityStates() {
        EnumSet<RouteValidityState> states = EnumSet.noneOf(RouteValidityState.class);
        for (String state : routeValidityStates.split(",")) {
            states.add(RouteValidityState.valueOf(state));
        }
        return states;
    }

    public static String normEmail(final String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static Set<String> normEmail(final List<String> emails) {
        return emails.stream().map(RoaAlertConfiguration::normEmail).collect(Collectors.toSet());
    }
}
