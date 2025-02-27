package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.SubscribeToRoaAlertCommand;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailTokens;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Handler
public class SubscribeToRoaAlertCommandHandler extends AbstractCertificateAuthorityCommandHandler<SubscribeToRoaAlertCommand> {

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
                makeParameters(configuration.toData()),
                EmailTokens.uniqueId(configuration.getCertificateAuthority().getUuid()));
    }

    private EmailSender.EmailTemplates getConfirmationTemplate(RoaAlertConfiguration configuration) {
        return switch (configuration.getFrequency()) {
            case DAILY -> EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_DAILY;
            case WEEKLY -> EmailSender.EmailTemplates.ROA_ALERT_SUBSCRIBE_CONFIRMATION_WEEKLY;
        };
    }

    private void updateConfigurationAndSendConfirmation(RoaAlertConfiguration configuration, SubscribeToRoaAlertCommand command) {
        RoaAlertConfigurationData oldConfiguration = configuration.toData();
        configuration.setSubscription(new RoaAlertSubscriptionData(List.of(command.getEmail()),
                command.getRouteValidityStates(), command.getFrequency(), command.isNotifyOnRoaChanges()));
        RoaAlertConfigurationData newConfiguration = configuration.toData();

        var oldEmailAddress = oldConfiguration.getEmails()
                .stream().map(RoaAlertConfiguration::normEmail).collect(Collectors.toSet());

        var normNewEmail = RoaAlertConfiguration.normEmail(command.getEmail());
        if (!oldEmailAddress.contains(normNewEmail)) {
            var parametersSubscribe = makeParameters(newConfiguration, command.isNotifyOnRoaChanges());
            var emailTemplate = getConfirmationTemplate(configuration);
            emailSender.sendEmail(normNewEmail, emailTemplate.templateSubject, emailTemplate, parametersSubscribe,
                    EmailTokens.uniqueId(configuration.getCertificateAuthority().getUuid()));
        }
    }

    public static Map<String, Object> makeParameters(RoaAlertConfigurationData configuration) {
        return makeParameters(configuration, false);
    }

    public static Map<String, Object> makeParameters(RoaAlertConfigurationData configuration, boolean notifyOnChange) {
        return Map.of("subscription", configuration,
                "roaChangeSubscription", notifyOnChange ?
                        "Also you are subscribed to alerts about ROA changes." : "");
    }

    private RoaAlertConfiguration createConfiguration(SubscribeToRoaAlertCommand command) {
        ManagedCertificateAuthority certificateAuthority = lookupManagedCa(command.getCertificateAuthorityId());
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority);
        configuration.setSubscription(new RoaAlertSubscriptionData(List.of(command.getEmail()),
                command.getRouteValidityStates(), command.getFrequency(), command.isNotifyOnRoaChanges()));
        repository.add(configuration);
        return configuration;
    }
}
