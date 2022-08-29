package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.EmailSender;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;


@Handler
public class SubscribeToRoaAlertCommandHandler extends AbstractCertificateAuthorityCommandHandler<SubscribeToRoaAlertCommand> {

    private static final String SUBSCRIBE_SUBJECT = "Your Resource Certification (RPKI) alerts subscription";

    private final RoaAlertConfigurationRepository repository;

    private final EmailSender emailSender;

    @Inject
    public SubscribeToRoaAlertCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository, RoaAlertConfigurationRepository repository, EmailSender emailSender) {
        super(certificateAuthorityRepository);
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    public Class<SubscribeToRoaAlertCommand> commandType() {
        return SubscribeToRoaAlertCommand.class;
    }

    @Override
    public void handle(SubscribeToRoaAlertCommand command, CommandStatus commandStatus) {
        RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(command.getCertificateAuthorityVersionedId().getId());
        if (configuration == null) {
            createConfigurationAndSendConfirmation(command);
        } else {
            updateConfigurationAndSendConfirmation(configuration, command);
        }
    }

    private void createConfigurationAndSendConfirmation(SubscribeToRoaAlertCommand command) {
        RoaAlertConfiguration configuration = createConfiguration(command);
        String emailTemplate = getConfirmationTemplate(configuration);

        emailSender.sendEmail(command.getEmail(), SUBSCRIBE_SUBJECT, emailTemplate,
                Collections.singletonMap("subscription", configuration.toData()));
    }

    private String getConfirmationTemplate(RoaAlertConfiguration configuration) {
        if(RoaAlertFrequency.WEEKLY.equals(configuration.getFrequency())) {
            return "email-templates/subscribe-confirmation-weekly.vm";
        } else {
            return "email-templates/subscribe-confirmation-daily.vm";
        }
    }

    private void updateConfigurationAndSendConfirmation(RoaAlertConfiguration configuration, SubscribeToRoaAlertCommand command) {
        RoaAlertConfigurationData oldConfiguration = configuration.toData();
        configuration.setSubscription(new RoaAlertSubscriptionData(command.getEmail(), command.getRouteValidityStates(), command.getFrequency()));
        RoaAlertConfigurationData newConfiguration = configuration.toData();

        final Set<String> oldEmailAddress = oldConfiguration.getEmails().stream().map(RoaAlertConfiguration::normEmail).collect(Collectors.toSet());
        final Set<String> newEmailAddress = newConfiguration.getEmails().stream().map(RoaAlertConfiguration::normEmail).collect(Collectors.toSet());

        if (oldEmailAddress.equals(newEmailAddress))
            return;

        for (String newEmail : newConfiguration.getEmails()) {
            if (!oldEmailAddress.contains(RoaAlertConfiguration.normEmail(newEmail))) {
                String emailTemplate = getConfirmationTemplate(configuration);
                emailSender.sendEmail(newEmail, SUBSCRIBE_SUBJECT, emailTemplate,
                        Collections.singletonMap("subscription", newConfiguration));
            }
        }

        for (String oldEmail : oldConfiguration.getEmails()) {
            if (!newEmailAddress.contains(RoaAlertConfiguration.normEmail(oldEmail))) {
                emailSender.sendEmail(oldEmail, UnsubscribeFromRoaAlertCommandHandler.UNSUBSCRIBE_SUBJECT,
                        "email-templates/unsubscribe-confirmation.vm", Collections.singletonMap("subscription", oldConfiguration));
            }
        }
    }

    private RoaAlertConfiguration createConfiguration(SubscribeToRoaAlertCommand command) {
        ManagedCertificateAuthority certificateAuthority = lookupManagedCa(command.getCertificateAuthorityVersionedId().getId());
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority);
        configuration.setSubscription(new RoaAlertSubscriptionData(command.getEmail(),
                command.getRouteValidityStates(), command.getFrequency()));
        repository.add(configuration);
        return configuration;
    }
}
