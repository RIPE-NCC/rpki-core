package net.ripe.rpki.hsm.db;

import net.ripe.rpki.domain.hsm.HsmCertificateChain;
import net.ripe.rpki.domain.hsm.HsmKey;
import net.ripe.rpki.domain.hsm.HsmKeyStore;
import net.ripe.rpki.domain.hsm.HsmKeyStoreRepository;
import net.ripe.rpki.hsm.api.KeyStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.*;

import static net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil.parseX509Certificate;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class DatabaseKeyStorage implements KeyStorage {

    private final HsmKeyStoreRepository hsmKeyStoreRepository;

    @Autowired
    public DatabaseKeyStorage(HsmKeyStoreRepository hsmKeyStoreRepository) {
        this.hsmKeyStoreRepository = hsmKeyStoreRepository;
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void storeEncryptedKeyAndCerts(final String keyStoreName, final String alias, final byte[] keyBlob, final Certificate[] chain) {
        try {
            final List<HsmCertificateChain> certificateChain = makeCertificateChain(chain);
            final HsmKey hsmKey = new HsmKey(keyBlob, alias, certificateChain);
            certificateChain.forEach(c -> c.setHsmKey(hsmKey));

            Optional<HsmKeyStore> optionalKeyStore = hsmKeyStoreRepository.findKeyStoreByName(keyStoreName);
            optionalKeyStore.ifPresentOrElse(
                keyStore -> {
                    hsmKey.setHsmKeyStore(keyStore);
                    keyStore.replaceKey(hsmKey);
                },
                () -> {
                    var keyStore = new HsmKeyStore(null, keyStoreName, Collections.singletonList(hsmKey));
                    hsmKey.setHsmKeyStore(keyStore);
                    hsmKeyStoreRepository.add(keyStore);
                }
            );
        } catch (CertificateEncodingException e) {
            throw new DatabaseKeyStorageException(e);
        }
    }

    @Override
    public byte[] getEncryptedKey(final String keyStoreName, final String alias) {
        return getHsmKey(keyStoreName, alias).getKeyBlob();
    }

    @Override
    public Certificate getCertificate(final String keyStoreName, final String alias) {
        final HsmKey hsmKey = getHsmKey(keyStoreName, alias);
        final List<HsmCertificateChain> chain = hsmKey.getCertificateChain();
        if (chain.isEmpty()) {
            throw new DatabaseKeyStorageException(String.format("empty certificate chain for '%s' key '%s'", keyStoreName, alias));
        }
        return parseX509Certificate(chain.get(0).getContent());
    }

    @Override
    public Certificate[] getCertificateChain(final String keyStoreName, final String alias) {
        final HsmKey hsmKey = getHsmKey(keyStoreName, alias);
        final List<HsmCertificateChain> certificateChain = hsmKey.getCertificateChain();
        final Certificate[] certificates = new Certificate[certificateChain.size()];
        int c = 0;
        for (final HsmCertificateChain cci : certificateChain) {
            certificates[c++] = parseX509Certificate(cci.getContent());
        }
        return certificates;
    }

    @Override
    public boolean containsAlias(final String keyStoreName, final String alias) {
        return hsmKeyStoreRepository.findKeyByKeyStoreAndAlias(keyStoreName, alias).isPresent();
    }

    @Override
    public void deleteEntry(final String keyStoreName, final String alias) {
        hsmKeyStoreRepository.deleteHsmKey(keyStoreName, alias);
    }

    @Override
    public Enumeration<String> aliases(final String keyStoreName) {
        return hsmKeyStoreRepository.listAliases(keyStoreName);
    }

    @Override
    public int keystoreSize(final String keyStoreName) {
        return hsmKeyStoreRepository.findKeyStoreByName(keyStoreName)
            .map(x -> x.getHsmKeys().size())
            .orElseThrow(() -> new DatabaseKeyStorageException(String.format("key store '%s' not found", keyStoreName)));
    }

    @Override
    public void storeHmacKey(final String keyStoreName, final byte[] hmacBlob) {
        HsmKeyStore keyStore = hsmKeyStoreRepository.findKeyStoreByName(keyStoreName)
            .orElseGet(() ->  new HsmKeyStore(null, keyStoreName, new ArrayList<>(0)));
        keyStore.setHmac(hmacBlob);
        hsmKeyStoreRepository.merge(keyStore);
    }

    @Override
    public byte[] getHmacKey(final String keyStoreName) {
        return hsmKeyStoreRepository.findKeyStoreByName(keyStoreName)
            .map(HsmKeyStore::getHmac)
            .orElseThrow(() -> new DatabaseKeyStorageException(String.format("key store '%s' not found", keyStoreName)));
    }

    @Override
    public Enumeration<String> listKeyStores() {
        return hsmKeyStoreRepository.listKeyStores();
    }

    private HsmKey getHsmKey(String keyStoreName, String alias) {
        return hsmKeyStoreRepository.findKeyByKeyStoreAndAlias(keyStoreName, alias)
            .orElseThrow(() -> new DatabaseKeyStorageException(String.format("key store '%s' with key '%s' not found", keyStoreName, alias)));
    }

    private List<HsmCertificateChain> makeCertificateChain(final Certificate[] chain) throws CertificateEncodingException {
        final Certificate[] theChain = chain == null ? new Certificate[0] : chain;
        final List<HsmCertificateChain> certificateChain = new ArrayList<>(theChain.length);
        int counter = 1;
        for (final Certificate c : theChain) {
            certificateChain.add(new HsmCertificateChain(c.getEncoded(), counter++));
        }
        return certificateChain;
    }

}
