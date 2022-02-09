package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandHandlerMetrics {
    public static final String RPKICORE_COMMANDHANDLER_CALL = "rpkicore_commandhandler_call";
    public static final String RPKICORE_COMMANDHANDLER_CALL_HELP = "Number of executed commands by <handler> and <status>";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<CertificateAuthorityCommandHandler<?>, Metrics> handlerMetrics = new ConcurrentHashMap<>();

    @Autowired
    public CommandHandlerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Metrics track(CertificateAuthorityCommandHandler<?> handler) {
        return handlerMetrics.computeIfAbsent(handler, theHandler -> new Metrics(meterRegistry, theHandler));
    }

    public final static class Metrics {
        private final Counter callFailure;
        private final Counter callNoEffect;
        private final Counter callSuccess;

        private Metrics(MeterRegistry registry, CertificateAuthorityCommandHandler<?> handler) {
            callFailure = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag("handler", handlerName(handler))
                    .tag("status", "failure")
                    .register(registry);
            callSuccess = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag("handler", handlerName(handler))
                    .tag("status", "success")
                    .register(registry);
            callNoEffect = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag("handler", handlerName(handler))
                    .tag("status", "noop")
                    .register(registry);
        }

        private String handlerName(CertificateAuthorityCommandHandler<?> type) {
            return type.getClass().getSimpleName().replaceAll("(Command)?Handler$", "");
        }

        public void success() {
            callSuccess.increment();
        }

        public void noEffect() {
            callNoEffect.increment();
        }

        public void failure() {
            callFailure.increment();
        }
    }
}
