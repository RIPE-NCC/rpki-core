package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import org.apache.commons.lang.Validate;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Subscribe an email address to alerts about BGP updates seen by RIS
 * that are invalidated by the CA's ROAs.
 */
@Getter
public class SubscribeToRoaAlertCommand extends CertificateAuthorityCommand {

    private final String email;
    private final Collection<RouteValidityState> routeValidityStates;
    private final RoaAlertFrequency frequency;

    public SubscribeToRoaAlertCommand(VersionedId certificateAuthorityId, String email,
                                      Collection<RouteValidityState> routeValidityStates) {
        this(certificateAuthorityId, email, routeValidityStates, RoaAlertFrequency.DAILY);
    }

    public SubscribeToRoaAlertCommand(VersionedId certificateAuthorityId, String email,
                                      Collection<RouteValidityState> routeValidityStates,
                                      RoaAlertFrequency frequency) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        Validate.notEmpty(email, "email is required");
        Validate.notEmpty(routeValidityStates, "routeValidityStates is required");
        this.email = email;
        this.routeValidityStates = EnumSet.copyOf(routeValidityStates);
        this.frequency = frequency;
    }

    // Let's make this conform to human repre
    private String validitySummary(){
        if(routeValidityStates.contains(RouteValidityState.UNKNOWN))
            return "invalid and unknown announcements.";
        else
            return "invalid announcements only.";
    }

    @Override
    public String getCommandSummary() {
        return "Subscribed " + email + " to " + frequency.toString().toLowerCase() + " ROA alerts for "+validitySummary();
    }
}
