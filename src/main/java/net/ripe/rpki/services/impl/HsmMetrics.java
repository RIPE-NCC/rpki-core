package net.ripe.rpki.services.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import net.ripe.rpki.hsm.Keys;
import org.springframework.stereotype.Component;

@Component
public class HsmMetrics {
    private final Gauge metric;

    public HsmMetrics(Keys keys, CertificationProviderConfigurationData providerConfiguration, MeterRegistry meterRegistry) {
        metric = Gauge.builder("rpkicore.hsm.keystore", () -> 1)
                .tag("vendor", keys.keystoreVendor())
                .tag("keystore-provider", providerConfiguration.getKeyStoreType())
                .tag("keypair-provider", providerConfiguration.getKeyPairGeneratorProvider())
                .tag("signature-provider", providerConfiguration.getSignatureProvider())
                .register(meterRegistry);
    }
}
