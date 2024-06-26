package net.ripe.rpki.services.impl.handlers;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class MessageDispatcher {
    private List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> handlers = new ArrayList<>();

    @Setter
    @Autowired
    private ApplicationContext applicationContext;

    @Setter
    @Autowired
    private CommandHandlerMetrics metrics;

    @PostConstruct
    public void init() {
        handlers = makeOrderedHandlerList();
    }

    @SuppressWarnings("unchecked")
    protected List<CertificateAuthorityCommandHandler<CertificateAuthorityCommand>> makeOrderedHandlerList() {
        final Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(Handler.class);
        return beansWithAnnotation.values().stream()
                .map(bean -> (CertificateAuthorityCommandHandler<CertificateAuthorityCommand>) bean)
                .sorted(Comparator.comparingInt(MessageDispatcher::orderOf)).toList();
    }

    private static int orderOf(Object bean) {
        return bean.getClass().getAnnotation(Handler.class).order();
    }

    public void dispatch(CertificateAuthorityCommand command, CommandStatus commandStatus) {
        Validate.notNull(command);
        handlers.stream()
                .filter(handler -> handler.commandType().isInstance(command))
                .forEach(handler -> {
                    final CommandHandlerMetrics.Metrics sample = metrics.track(handler);
                    try {
                        sample.record(() -> handler.handle(command, commandStatus));
                        sample.success();
                    } catch (CommandWithoutEffectException e) {
                        sample.noEffect();
                        throw e;
                    } catch (OptimisticLockException | PessimisticLockException | TransientDataAccessException e) {
                        sample.transactionNotSerializable();
                        throw e;
                    } catch (Exception e) {
                        sample.failure();
                        throw e;
                    }
                });
    }
}
