package net.ripe.rpki.hsm.db;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateBuilderHelper;
import net.ripe.rpki.domain.hsm.HsmCertificateChainRepository;
import net.ripe.rpki.domain.hsm.HsmKeyRepository;
import net.ripe.rpki.domain.hsm.HsmKeyStoreRepository;
import net.ripe.rpki.hsm.api.KeyStorage;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class DatabaseKeyStorageTest {

    private static final String KEY_STORE_1 = "keyStore1";
    private static final String KEY_STORE_2 = "keyStore2";

    private static final String ALIAS_1 = "alias1";
    private static final String ALIAS_2 = "alias2";
    private static final String ALIAS_3 = "alias3";
    private static final String ALIAS_4 = "alias4";
    private static final byte[] KEY_BLOB = KeyPairFactoryTest.TEST_KEY_PAIR.getPublic().getEncoded();

    @Autowired
    private KeyStorage hsmDatabaseService;

    @Autowired
    private HsmKeyStoreRepository hsmKeyStoreRepository;

    @Autowired
    private HsmKeyRepository hsmKeyRepository;

    @Autowired
    private HsmCertificateChainRepository hsmCertificateChainRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Before
    public void setUp() {
        transactionTemplate.setReadOnly(false);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                hsmCertificateChainRepository.removeAll();
                hsmKeyRepository.removeAll();
                hsmKeyStoreRepository.removeAll();
            }
        });
    }

    @Test
    public void storeAndExtractKeyBlob() {
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[0]);

        byte[] encriptedKey = hsmDatabaseService.getEncryptedKey(KEY_STORE_1, ALIAS_1);
        Assert.assertArrayEquals(KEY_BLOB, encriptedKey);
    }

    @Test
    public void storeAndCheckAliases() {
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[0]);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_2, KEY_BLOB, new Certificate[0]);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_2, ALIAS_3, KEY_BLOB, new Certificate[0]);

        Assert.assertEquals(
                new HashSet<>(Arrays.asList(ALIAS_1, ALIAS_2)),
                new HashSet<>(Collections.list(hsmDatabaseService.aliases(KEY_STORE_1)))
        );

        Assert.assertEquals(
                new HashSet<>(Collections.singletonList(ALIAS_3)),
                new HashSet<>(Collections.list(hsmDatabaseService.aliases(KEY_STORE_2)))
        );

        Assert.assertTrue(hsmDatabaseService.containsAlias(KEY_STORE_1, ALIAS_1));
        Assert.assertTrue(hsmDatabaseService.containsAlias(KEY_STORE_1, ALIAS_2));
        Assert.assertTrue(hsmDatabaseService.containsAlias(KEY_STORE_2, ALIAS_3));
        Assert.assertFalse(hsmDatabaseService.containsAlias(KEY_STORE_2, ALIAS_4));
    }

    @Test
    public void storeAndCheckKeyStores() {
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[0]);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_2, KEY_BLOB, new Certificate[0]);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_2, ALIAS_3, KEY_BLOB, new Certificate[0]);

        Assert.assertEquals(
                new HashSet<>(Arrays.asList(KEY_STORE_1, KEY_STORE_2)),
                new HashSet<>(Collections.list(hsmDatabaseService.listKeyStores()))
        );

        Assert.assertEquals(2, hsmDatabaseService.keystoreSize(KEY_STORE_1));
        Assert.assertEquals(1, hsmDatabaseService.keystoreSize(KEY_STORE_2));
    }

    @Test
    public void storeAndCheckCertificateChain() {
        final X509Certificate certificate1 = createCertificate(KeyPairFactoryTest.TEST_KEY_PAIR);
        final X509Certificate certificate2 = createCertificate(KeyPairFactoryTest.SECOND_TEST_KEY_PAIR);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[]{certificate1, certificate2});

        Assert.assertEquals(hsmDatabaseService.getCertificate(KEY_STORE_1, ALIAS_1), certificate1);

        Assert.assertArrayEquals(
                new Certificate[]{certificate1, certificate2},
                hsmDatabaseService.getCertificateChain(KEY_STORE_1, ALIAS_1)
        );
    }

    @Test
    public void deleteEntryAndCheckConsequences() {
        final X509Certificate certificate1 = createCertificate(KeyPairFactoryTest.TEST_KEY_PAIR);
        final X509Certificate certificate2 = createCertificate(KeyPairFactoryTest.SECOND_TEST_KEY_PAIR);
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[]{certificate1});
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_2, KEY_BLOB, new Certificate[]{certificate1, certificate2});
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_2, ALIAS_3, KEY_BLOB, new Certificate[0]);

        hsmDatabaseService.deleteEntry(KEY_STORE_1, ALIAS_2);

        Assert.assertEquals(
                new HashSet<>(Collections.singletonList(ALIAS_1)),
                new HashSet<>(Collections.list(hsmDatabaseService.aliases(KEY_STORE_1)))
        );

        Assert.assertEquals(0, hsmDatabaseService.getCertificateChain(KEY_STORE_1, ALIAS_2).length);

        hsmDatabaseService.deleteEntry(KEY_STORE_2, ALIAS_3);

        Assert.assertEquals(
                new HashSet<>(Collections.emptyList()),
                new HashSet<>(Collections.list(hsmDatabaseService.aliases(KEY_STORE_2)))
        );
    }

    @Test
    public void storeAndGetHmacKey() {
        hsmDatabaseService.storeEncryptedKeyAndCerts(KEY_STORE_1, ALIAS_1, KEY_BLOB, new Certificate[0]);
        byte[] hmac = {1, 2, 3, 4, 5, 6, 7};
        hsmDatabaseService.storeHmacKey(KEY_STORE_1, hmac);

        Assert.assertArrayEquals(
                hmac,
                hsmDatabaseService.getHmacKey(KEY_STORE_1)
        );
    }

    private static Random random = new Random();

    private static X509Certificate createCertificate(KeyPair keyPair) {
        final X509CertificateBuilderHelper builder = new X509CertificateBuilderHelper();
        builder.withSignatureProvider("SunRsaSign");
        builder.withSerial(BigInteger.ONE);
        builder.withValidityPeriod(new ValidityPeriod(new DateTime().minusYears(2), new DateTime().minusYears(1)));
        builder.withCa(false);
        builder.withIssuerDN(new X500Principal("CN=issuer" + random.nextInt()));
        builder.withSubjectDN(new X500Principal("CN=subject" + random.nextInt()));
        builder.withSigningKeyPair(keyPair);
        builder.withPublicKey(keyPair.getPublic());
        return builder.generateCertificate();
    }
}