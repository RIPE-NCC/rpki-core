package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.util.KeyStoreUtilTest;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.provisioning.ProvisioningObjectMother;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509CRL;

import static org.junit.Assert.*;


public class DownStreamProvisioningCommunicatorTest {

    private DownStreamProvisioningCommunicator subject;
    private KeyPair expectedKeyPair;

    private final X500Principal ID_CERT_SUBJECT = new X500Principal("CN=test");

    @Before
    public void setUp() {
        expectedKeyPair = KeyPairFactoryTest.TEST_KEY_PAIR;
        KeyPairEntityKeyInfo keyInfo = new KeyPairEntityKeyInfo("test", expectedKeyPair);
        KeyPairEntitySignInfo signInfo = new KeyPairEntitySignInfo(KeyStoreUtilTest.DEFAULT_KEYSTORE_PROVIDER, KeyPairFactoryTest.DEFAULT_KEYPAIR_GENERATOR_PROVIDER, KeyStoreUtilTest.DEFAULT_KEYSTORE_TYPE);

        subject = new DownStreamProvisioningCommunicator(keyInfo, signInfo, ID_CERT_SUBJECT);
    }

    @Test
    public void shouldCreateKeyPairWhenInitialised() {
        KeyPair actualKeyPair = subject.getKeyPair();

        assertNotNull(actualKeyPair);
        assertEquals(expectedKeyPair.getPublic(), actualKeyPair.getPublic());
        assertEquals(expectedKeyPair.getPrivate(), actualKeyPair.getPrivate());
    }


    @Test
    public void shouldCreateProvisioningIdCert() {
        ProvisioningIdentityCertificate identityCertificate = subject.getProvisioningIdentityCertificate();
        assertNotNull(identityCertificate);
    }

    @Test
    public void shouldCreateCrl() {
        X509CRL crl = subject.getProvisioningCrl();
        assertNotNull(crl);
    }

    @Test
    public void shouldCreateResponseCmsObjectForPayload() {
        // Note the communicator does not care about the payload, so using the cheap to make resource class list query payload here.
        ProvisioningCmsObject responseObject = subject.createProvisioningCmsResponseObject(PregeneratedKeyPairFactory.getInstance(), ProvisioningObjectMother.RESOURCE_CLASS_LIST_QUERY_PAYLOAD);

        assertNotNull(responseObject);

        AbstractProvisioningPayload payload = responseObject.getPayload();
        assertTrue(payload instanceof ResourceClassListQueryPayload);
    }



}
