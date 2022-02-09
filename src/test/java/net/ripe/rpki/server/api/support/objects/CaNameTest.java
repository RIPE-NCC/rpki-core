package net.ripe.rpki.server.api.support.objects;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CaNameTest {


    @Test(expected = IllegalArgumentException.class)
    public void shouldNotBeNull() {
        CaName.of((String)null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotBeEmpty() {
        CaName.of("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotBeBlank() {
        CaName.of("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldBanInvalidX500Principal() {
        final X500Principal principal = new X500Principal("c=nl");
        CaName.of(principal);
    }

    @Test
    public void shouldConstructCaName() {
        assertEquals("O=ORG-1", CaName.of("org-1").toString());
        assertEquals("CN=123", CaName.of(123).toString());
        assertEquals("O=ORG-1", CaName.of(new X500Principal("o=org-1")).toString());
    }

    @Test
    public void shouldGetMembershipIdForMember() {
        assertEquals(123L, CaName.of(123).getMembershipId().longValue());
    }

    @Test
    public void shouldGetOrganisationIdForOrganisation() {
        assertEquals("ORG-1", CaName.of("org-1").getOrganisationId());
    }

    @Test
    public void shouldGetX500Principal() {
        assertEquals(new X500Principal("cn=123"), CaName.of(123).getPrincipal());
        assertEquals(new X500Principal("o=org-1"), CaName.of("org-1").getPrincipal());
        assertEquals(new X500Principal("o=org-1"), CaName.of(new X500Principal("O=ORG-1")).getPrincipal());
    }

    @Test
    public void shouldReturnNullMembershipIdForNonMember() {
        assertNull(CaName.of("org-1").getMembershipId());
    }

    @Test
    public void shouldReturnNullOrganisationIdForOrganisation() {
        assertNull(CaName.of(123).getOrganisationId());
    }
}
