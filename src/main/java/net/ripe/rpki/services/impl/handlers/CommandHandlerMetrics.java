package net.ripe.rpki.services.impl.handlers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandHandlerMetrics {
    // command handler should be two words, but the joined version is used in alerts.
    private static final String RPKICORE_COMMANDHANDLER_CALL = "rpkicore.commandhandler.call";
    private static final String RPKICORE_COMMANDHANDLER_CALL_HELP = "Number of executed commands by <handler> and <status>";

    private static final String TAG_HANDLER = "handler";
    private static final String TAG_STATUS = "status";


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

        private final Timer executionTimer;

        private Metrics(MeterRegistry registry, CertificateAuthorityCommandHandler<?> handler) {
            callFailure = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag(TAG_HANDLER, handlerName(handler))
                    .tag(TAG_STATUS, "failure")
                    .register(registry);
            callSuccess = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag(TAG_HANDLER, handlerName(handler))
                    .tag(TAG_STATUS, "success")
                    .register(registry);
            callNoEffect = Counter.builder(RPKICORE_COMMANDHANDLER_CALL)
                    .description(RPKICORE_COMMANDHANDLER_CALL_HELP)
                    .tag(TAG_HANDLER, handlerName(handler))
                    .tag(TAG_STATUS, "noop")
                    .register(registry);

            executionTimer = Timer.builder("rpkicore.commandhandler.duration")
                    .description("Execution time of commands per handler type")
                    .tag(TAG_HANDLER, handlerName(handler))
                    .publishPercentileHistogram()
                    .maximumExpectedValue(Duration.ofSeconds(10))
                    .register(registry);
        }

        private String handlerName(CertificateAuthorityCommandHandler<?> type) {
            return type.getClass().getSimpleName().replaceAll("(Command)?Handler$", "");
        }

        public void record(Runnable f) {
            this.executionTimer.record(f);
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
