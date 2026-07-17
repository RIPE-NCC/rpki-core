package net.ripe.rpki.domain.bgpsec;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CsrTest {
    @Test
    public void testKeyIdentifier() {
        var csr = "MIH7MIGiAgEAMBoxGDAWBgNVBAMMD1JPVVRFUi0wMDAwM0NDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABE9dBTAcT+j96+mhvyAqX7JLae1+spSSGPCsnus5EITTrdMvnEc2J4B/DBs2N3Fzb2euM+AqWdtoH+LXsmxqvKOgJjAkBgkqhkiG9w0BCQ4xFzAVMBMGA1UdJQQMMAoGCCsGAQUFBwMeMAoGCCqGSM49BAMCA0gAMEUCIQCKJSWZeF7XHuHkFeAN7zOzhEgM+6WyaklaIo3J3lRPmgIgD9kPSO0AjVf1cEUnQrgC5D/5SMaUJ2hp3r8joKFq3hA=";
        assertEquals("17316903F0671229E8808BA8E8AB0105FA915A07", Csr.getKeyIdentifier(csr));
    }
}