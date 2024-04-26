package net.ripe.rpki.services.impl.handlers;

import com.google.common.collect.Sets;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.EmailSender;

import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;


@Handler
public class SubscribeToRoaAlertCommandHandler extends AbstractCertificateAuthorityCommandHandler<SubscribeToRoaAlertCommand> {
    public static final String SUBSCRIPTION = "subscription";

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
        RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(command.getCertificateAuthorityId());
        if (configuration == null) {
            createConfigurationAndSendConfirmation(command);
        } else {
            updateConfigurationAndSendConfirmation(configuration, command);
        }
    }

    private void createConfigurationAndSendConfirmation(SubscribeToRoaAlertCommand command) {
        RoaAlertConfiguration configuration = createConfiguration(command);
        var emailTemplate = getConfirmationTemplate(configuration);

        emailSender.sendEmail(command.getEmail(), emailTemplate.templateSubject, emailTemplate,
                Collections.singletonMap(SUBSCRIPTION, configuration.toData()));
    }

    private EmailSender.EmailTemplates getConfirmationTemplate(RoaAlertConfiguration configuration) {
        switch (configuration.getFrequency()) {
            case DAILY:
                return EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY;
            case WEEKLY:
                return EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY;
            default:
                throw new IllegalStateException("Frequency should not be null");
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

        // elements LHS not in RHS
        Sets.difference(newEmailAddress, oldEmailAddress).forEach(email -> {
            var emailTemplate = getConfirmationTemplate(configuration);
            emailSender.sendEmail(email, emailTemplate.templateSubject, emailTemplate,
                    Collections.singletonMap(SUBSCRIPTION, newConfiguration));
        });

        Sets.difference(oldEmailAddress, newEmailAddress).forEach(email -> {
            emailSender.sendEmail(email, EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE.templateSubject, EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE, Collections.singletonMap(SUBSCRIPTION, oldConfiguration));
        });
    }

    private RoaAlertConfiguration createConfiguration(SubscribeToRoaAlertCommand command) {
        ManagedCertificateAuthority certificateAuthority = lookupManagedCa(command.getCertificateAuthorityId());
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority);
        configuration.setSubscription(new RoaAlertSubscriptionData(command.getEmail(),
                command.getRouteValidityStates(), command.getFrequency()));
        repository.add(configuration);
        return configuration;
    }
}
