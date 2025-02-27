package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.security.CertificationUserId;
import net.ripe.rpki.services.impl.email.EmailSender;
import net.ripe.rpki.services.impl.email.EmailTokens;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class RoaNotificationService {

    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    private final EmailSender emailSender;
    private final InternalNamePresenter internalNamePresenter;

    @Autowired
    public RoaNotificationService(RoaAlertConfigurationRepository roaAlertConfigurationRepository,
                                  EmailSender emailSender,
                                  InternalNamePresenter internalNamePresenter) {
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
        this.emailSender = emailSender;
        this.internalNamePresenter = internalNamePresenter;
    }

    public List<EmailSender.ResultingEmail> notifyAboutRoaChanges(ManagedCertificateAuthority ca,
                                                                  CertificationUserId userId,
                                                                  List<RoaConfigurationPrefix> added,
                                                                  List<RoaConfigurationPrefix> removed) {
        RoaAlertConfiguration configuration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(ca.getId());
        if (configuration == null || !configuration.isNotifyOnRoaChanges() || (added.isEmpty() && removed.isEmpty())) {
            return Collections.emptyList();
        }
        RoaAlertSubscriptionData subscriptionOrNull = configuration.getSubscriptionOrNull();
        if (subscriptionOrNull == null) {
            return Collections.emptyList();
        }
        var humanizedCaName = internalNamePresenter.humanizeCaName(ca.getName());
        var ssoEmail = internalNamePresenter.humanizeUserPrincipal(userId.getId().toString());

        var parameters = Map.of(
                "humanizedCaName", humanizedCaName,
                "roas", showRoas(added, removed),
                "ssoEmail", ssoEmail
        );

        return subscriptionOrNull.getEmails().stream()
                .map(email -> emailSender.sendEmail(
                        email,
                        String.format(EmailSender.EmailTemplates.ROA_CHANGE_ALERT.templateSubject, humanizedCaName),
                        EmailSender.EmailTemplates.ROA_CHANGE_ALERT,
                        parameters,
                        EmailTokens.uniqueId(ca.getUuid())))
                .toList();
    }

    public record Roa(String asn, String prefix, String maxLength, char operation) {
    }

    private List<Roa> showRoas(List<RoaConfigurationPrefix> added, List<RoaConfigurationPrefix> removed) {
        BiFunction<Object, Integer, String> padded = (o, size) ->
                StringUtils.rightPad(o.toString(), size);

        BiFunction<RoaConfigurationPrefix, Character, Roa> textRoa = (r, operation) -> {
            IpRange ip = r.getPrefix();
            return new Roa(
                    padded.apply(r.getAsn().longValue(), 10),
                    padded.apply(ip, 50),
                    padded.apply(r.getMaximumLength(), 12), operation);
        };

        return Stream.concat(
                        added.stream().map(r -> Triple.of(r.getAsn(), r.getPrefix(), textRoa.apply(r, 'A'))),
                        removed.stream().map(r -> Triple.of(r.getAsn(), r.getPrefix(), textRoa.apply(r, 'D')))
                )
                .sorted(Comparator.comparing((Triple<Asn, IpRange, Roa> t) -> t.getLeft()).thenComparing(Triple::getMiddle))
                .map(Triple::getRight)
                .toList();
    }

}
