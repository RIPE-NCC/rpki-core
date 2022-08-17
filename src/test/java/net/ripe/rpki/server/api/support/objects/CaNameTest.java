package net.ripe.rpki.server.api.support.objects;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CaNameTest {

    @Test(expected = CaName.BadCaNameException.class)
    public void shouldNotBeNull() {
        CaName.fromOrganisationId(null);
    }

    @Test(expected = CaName.BadCaNameException.class)
    public void shouldNotBeEmpty() {
        CaName.fromOrganisationId("");
    }

    @Test(expected = CaName.BadCaNameException.class)
    public void shouldNotBeBlank() {
        CaName.fromOrganisationId("  ");
    }

    @Test(expected = CaName.BadCaNameException.class)
    public void shouldBanInvalidX500Principal() {
        final X500Principal principal = new X500Principal("c=nl");
        CaName.of(principal);
    }

    @Test(expected = CaName.BadCaNameException.class)
    public void shouldRejectInvalidX500Principal() {
        CaName.parse("CN=foo, =bar");
    }

    @Test
    public void shouldPreserveCaseOfCaName() {
        assertEquals("CN=ALL Resources, O=RIPE NCC, C=NL", CaName.parse("CN=ALL Resources,O=RIPE NCC,C=NL").toString());
        assertEquals("CN=ALL Resources, O=RIPE NCC, C=NL", CaName.of(new X500Principal("CN=ALL Resources,O=RIPE NCC,C=NL")).toString());
    }

    @Test
    public void shouldConstructCaName() {
        assertEquals("O=ORG-1", CaName.fromOrganisationId("org-1").toString());
        assertEquals("CN=123", CaName.fromMembershipId(123).toString());
        assertEquals("O=org-1", CaName.of(new X500Principal("o=org-1")).toString());
    }

    @Test
    public void shouldGetMembershipIdForMember() {
        assertEquals(123L, CaName.fromMembershipId(123).getMembershipId().longValue());
    }

    @Test
    public void shouldGetOrganisationIdForOrganisation() {
        assertEquals("ORG-1", CaName.fromOrganisationId("org-1").getOrganisationId());
    }

    @Test
    public void shouldGetX500Principal() {
        assertEquals(new X500Principal("cn=123"), CaName.fromMembershipId(123).getPrincipal());
        assertEquals(new X500Principal("o=org-1"), CaName.fromOrganisationId("org-1").getPrincipal());
        assertEquals(new X500Principal("o=org-1"), CaName.of(new X500Principal("O=ORG-1")).getPrincipal());
    }

    @Test
    public void shouldReturnNullMembershipIdForNonMember() {
        assertNull(CaName.fromOrganisationId("org-1").getMembershipId());
    }

    @Test
    public void shouldReturnNullOrganisationIdForOrganisation() {
        assertNull(CaName.fromMembershipId(123).getOrganisationId());
    }
}
