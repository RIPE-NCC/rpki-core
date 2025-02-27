package net.ripe.rpki.services.impl.handlers;

import jakarta.inject.Inject;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateRoaChangeAlertCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailTokens;

import java.util.Collections;


@Handler
public class UpdateRoaChangeAlertCommandHandler extends AbstractCertificateAuthorityCommandHandler<UpdateRoaChangeAlertCommand> {

    private final RoaAlertConfigurationRepository repository;

    private final EmailSender emailSender;

    @Inject
    public UpdateRoaChangeAlertCommandHandler(CertificateAuthorityRepository certificateAuthorityRepository,
                                              RoaAlertConfigurationRepository repository,
                                              EmailSender emailSender) {
        super(certificateAuthorityRepository);
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    public Class<UpdateRoaChangeAlertCommand> commandType() {
        return UpdateRoaChangeAlertCommand.class;
    }

    @Override
    public void handle(UpdateRoaChangeAlertCommand command, CommandStatus commandStatus) {
        final RoaAlertConfiguration configuration = repository.findByCertificateAuthorityIdOrNull(command.getCertificateAuthorityId());
        if (configuration == null) {
            createNewAlertConfiguration(command, command.isNotifyOnRoaChanges());
            // there wasn't any configuration, so no emails to notify
        } else if (command.isNotifyOnRoaChanges() != configuration.isNotifyOnRoaChanges()) {
            configuration.setNotifyOnRoaChanges(command.isNotifyOnRoaChanges());
            EmailSender.EmailTemplates template = command.isNotifyOnRoaChanges() ?
                    EmailSender.EmailTemplates.ROA_CHANGE_ALERT_SUBSCRIBE_CONFIRMATION :
                    EmailSender.EmailTemplates.ROA_CHANGE_ALERT_UNSUBSCRIBE_CONFIRMATION;
            sendEmails(configuration, template);
        }
    }

    private void sendEmails(RoaAlertConfiguration newConfiguration, EmailSender.EmailTemplates template) {
        newConfiguration.getSubscriptionOrNull().getEmails().forEach(email ->
                emailSender.sendEmail(email,
                        template.templateSubject, template,
                        Collections.singletonMap("subscription", newConfiguration),
                        EmailTokens.uniqueId(newConfiguration.getCertificateAuthority().getUuid())));
    }

    private void createNewAlertConfiguration(UpdateRoaChangeAlertCommand command, boolean notifyOnRoaChanges) {
        ManagedCertificateAuthority certificateAuthority = lookupManagedCa(command.getCertificateAuthorityId());
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority);
        configuration.setNotifyOnRoaChanges(notifyOnRoaChanges);
        repository.add(configuration);
    }
}
