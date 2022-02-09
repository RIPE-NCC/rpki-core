package net.ripe.rpki.hsm.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.ripe.rpki.hsm.api.KeyStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Enumeration;

/**
 * Decorated implementation of DatabaseInterface that extends the standard HsmDatabaseService with timing measurements.
 * Annotated to be preferred (@Primary) over the non-timed implementation.
 */
@Primary
@Component
public class TimingDatabaseKeyStorage implements KeyStorage {
    // The real backing DatabaseKeyStorage
    private final DatabaseKeyStorage databaseKeyStorage;

    private final Timer storeEncriptedKeyAndCertsTimer;
    private final Timer getEncriptedKeyTimer;
    private final Timer getCertificateTimer;
    private final Timer getCertificateChainTimer;
    private final Timer containsAliasTimer;
    private final Timer deleteEntryTimer;
    private final Timer aliasesTimer;
    private final Timer keyStoreSizeTimer;
    private final Timer storeHmacKeyTimer;
    private final Timer getHmacKeyTimer;
    private final Timer listKeyStoreTimer;

    @Autowired
    public TimingDatabaseKeyStorage(
        DatabaseKeyStorage databaseKeyStorage,
        MeterRegistry meterRegistry
    ) {
        this.databaseKeyStorage = databaseKeyStorage;

        storeEncriptedKeyAndCertsTimer = buildTimer(meterRegistry, "storeEncriptedKeyAndCerts");
        getEncriptedKeyTimer = buildTimer(meterRegistry, "getEncriptedKey");
        getCertificateTimer = buildTimer(meterRegistry, "getCertificate");
        getCertificateChainTimer = buildTimer(meterRegistry, "getCertificateChain");
        containsAliasTimer = buildTimer(meterRegistry, "containsAlias");
        deleteEntryTimer = buildTimer(meterRegistry, "deleteEntry");
        aliasesTimer = buildTimer(meterRegistry, "aliases");
        keyStoreSizeTimer = buildTimer(meterRegistry, "keyStoreSize");
        storeHmacKeyTimer = buildTimer(meterRegistry, "storeHmacKey");
        getHmacKeyTimer = buildTimer(meterRegistry, "getHmacKey");
        listKeyStoreTimer = buildTimer(meterRegistry, "listKeyStore");
    }

    private static Timer buildTimer(MeterRegistry meterRegistry, String name) {
        return Timer.builder("hsm.database.operation")
            .tag("operation", name)
            .description("Timing for nCipher DBprovider operations")
            .maximumExpectedValue(Duration.ofSeconds(1))
            .register(meterRegistry);
    }

    @Override
    public void storeEncryptedKeyAndCerts(String keyStoreName, String alias, byte[] keyBlob, Certificate[] chain) {
        storeEncriptedKeyAndCertsTimer.record(() -> databaseKeyStorage.storeEncryptedKeyAndCerts(keyStoreName, alias, keyBlob, chain));
    }

    @Override
    public byte[] getEncryptedKey(String keyStoreName, String alias) {
        return getEncriptedKeyTimer.record(() -> databaseKeyStorage.getEncryptedKey(keyStoreName, alias));
    }

    @Override
    public Certificate getCertificate(String keyStoreName, String alias) {
        return getCertificateTimer.record(() -> databaseKeyStorage.getCertificate(keyStoreName, alias));
    }

    @Override
    public Certificate[] getCertificateChain(String keyStoreName, String alias) {
        return getCertificateChainTimer.record(() -> databaseKeyStorage.getCertificateChain(keyStoreName, alias));
    }

    @Override
    public boolean containsAlias(String keyStoreName, String alias) {
        return containsAliasTimer.record(() -> databaseKeyStorage.containsAlias(keyStoreName, alias));
    }

    @Override
    public void deleteEntry(String keyStoreName, String alias) {
        deleteEntryTimer.record(() -> databaseKeyStorage.deleteEntry(keyStoreName, alias));
    }

    @Override
    public Enumeration<String> aliases(String keyStoreName) {
        return aliasesTimer.record(() -> databaseKeyStorage.aliases(keyStoreName));
    }

    @Override
    public int keystoreSize(String keyStoreName) {
        return keyStoreSizeTimer.record(() -> databaseKeyStorage.keystoreSize(keyStoreName));
    }

    @Override
    public void storeHmacKey(String keyStoreName, byte[] hmacBlob) {
        storeHmacKeyTimer.record(() -> databaseKeyStorage.storeHmacKey(keyStoreName, hmacBlob));
    }

    @Override
    public byte[] getHmacKey(String keyStoreName) {
        return getHmacKeyTimer.record(() -> databaseKeyStorage.getHmacKey(keyStoreName));
    }

    @Override
    public Enumeration<String> listKeyStores() {
        return listKeyStoreTimer.record(databaseKeyStorage::listKeyStores);
    }
    @Override
    public String getVersion() {
        return databaseKeyStorage.getVersion();
    }
}
