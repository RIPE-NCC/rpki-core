package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.UnsubscribeFromRoaAlertCommand;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailTokens;

import static net.ripe.rpki.domain.alerts.RoaAlertConfiguration.normEmail;

@Handler
@Slf4j
public class UnsubscribeFromRoaAlertCommandHandler extends AbstractCertificateAuthorityCommandHandler<UnsubscribeFromRoaAlertCommand> {

    private final RoaAlertConfigurationRepository repository;

    private final EmailSender emailSender;

    @Inject
    public UnsubscribeFromRoaAlertCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository, RoaAlertConfigurationRepository repository,
                                                 EmailSender emailSender) {
        super(certificateAuthorityRepository);
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    public Class<UnsubscribeFromRoaAlertCommand> commandType() {
        return UnsubscribeFromRoaAlertCommand.class;
    }

    @Override
    public void handle(UnsubscribeFromRoaAlertCommand command, CommandStatus commandStatus) {
        RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(command.getCertificateAuthorityId());
        RoaAlertSubscriptionData subscription = configuration == null ? null : configuration.getSubscriptionOrNull();
        if (subscription == null) {
            return;
        }
        configuration.setNotifyOnRoaChanges(command.isNotifyOnRoaChanges());
        if (!subscription.getEmails().contains(normEmail(command.getEmail()))) {
            log.info("Trying to unsubscribe the address {} that is not amongst subscribed addresses {}", command.getEmail(), subscription.getEmails());
            return;
        }
        configuration.removeEmail(command.getEmail());

        var parameters = SubscribeToRoaAlertCommandHandler.makeParameters(configuration.toData());
        emailSender.sendEmail(normEmail(command.getEmail()),
                EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE.templateSubject,
                EmailSender.EmailTemplates.ROA_ALERT_UNSUBSCRIBE,
                parameters,
                EmailTokens.uniqueId(configuration.getCertificateAuthority().getUuid()));
    }
}
