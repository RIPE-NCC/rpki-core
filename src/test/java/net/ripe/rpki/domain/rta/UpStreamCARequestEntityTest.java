package net.ripe.rpki.domain.rta;

import net.ripe.ipresource.IpResource;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.ta.domain.request.RevocationRequest;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


public class UpStreamCARequestEntityTest {

    @Test
    public void shouldUnderstandRevokeKey() {
        RevocationRequest revokeRequest = new RevocationRequest("test resource class", "CN=whoevah");
        List<TaRequest> requests = new ArrayList<>();
        requests.add(revokeRequest);
        TrustAnchorRequest trustAnchorRequest = new TrustAnchorRequest(URI.create("rsync://localhost:10873/ta/"),
                new X509CertificateInformationAccessDescriptor[0], requests);

        UpStreamCARequestEntity subject = new UpStreamCARequestEntity(null, trustAnchorRequest);

        // No equals on TrustAnchorRequest
        assertThat(subject.getUpStreamCARequest()).usingRecursiveComparison().isEqualTo(trustAnchorRequest);
    }

    @Test
    public void shouldParseOldStoredUpstreamCARequest() {
        String requestId = UUID.randomUUID().toString();
        String request =
                "<net.ripe.rpki.offline.requests.TrustAnchorRequest>\n" +
                "  <taRequests>\n" +
                "    <net.ripe.rpki.offline.requests.SigningRequest>\n" +
                "      <requestId>" + requestId + "</requestId>\n" +
                "      <resourceCertificateRequest>\n" +
                "        <ipResourceSet>193.0.0.0/8</ipResourceSet>\n" +
                "      </resourceCertificateRequest>\n" +
                "    </net.ripe.rpki.offline.requests.SigningRequest>\n" +
                "  </taRequests>\n" +
                "</net.ripe.rpki.offline.requests.TrustAnchorRequest>";

        UpStreamCARequestEntity subject = mkUpstreamCARequestWithXML(request);
        assertThat(subject.getUpStreamCARequest()).isNotNull().withFailMessage("Invalid UpStreamCARequest");
        TaRequest taRequest = subject.getUpStreamCARequest().getTaRequests().get(0);
        assertThat(requestId).isEqualTo(taRequest.getRequestId().toString());
        assertThat(taRequest).isInstanceOf(SigningRequest.class).withFailMessage("Parsed TA request should be a 'SigningRequest'");
        SigningRequest signingRequest = (SigningRequest) taRequest;
        assertThat(signingRequest.getResourceCertificateRequest().getIpResourceSet()).contains(IpResource.parse("193.0.0.0/8")).withFailMessage(
                "Signing request should container IP resource set 193.0.0.0/8"
        );
    }

    @Test
    public void shouldSerializeParsedUpstreamCARequest() {
        String request =
                "<net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest>\n" +
                "  <taRequests>\n" +
                "    <net.ripe.rpki.commons.ta.domain.request.SigningRequest>\n" +
                "      <requestId>" + UUID.randomUUID().toString() + "</requestId>\n" +
                "      <resourceCertificateRequest>\n" +
                "        <ipResourceSet>193.0.0.0/8</ipResourceSet>\n" +
                "      </resourceCertificateRequest>\n" +
                "    </net.ripe.rpki.commons.ta.domain.request.SigningRequest>\n" +
                "  </taRequests>\n" +
                "</net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest>";

        UpStreamCARequestEntity entity = mkUpstreamCARequestWithXML(request);
        UpStreamCARequestEntity subject = new UpStreamCARequestEntity(null, entity.getUpStreamCARequest());
        assertThat(request).isEqualTo(getUpstreamCARequestXML(subject));
    }

    private static UpStreamCARequestEntity mkUpstreamCARequestWithXML(String caRequestXml) {
        Field upStreamCARequest = upstreamCARequestField();

        UpStreamCARequestEntity entity = new UpStreamCARequestEntity();
        ReflectionUtils.setField(upStreamCARequest, entity, caRequestXml);
        return entity;
    }

    private static String getUpstreamCARequestXML(UpStreamCARequestEntity entity) {
        Field upStreamCARequest = upstreamCARequestField();
        return (String) ReflectionUtils.getField(upStreamCARequest, entity);
    }

    private static Field upstreamCARequestField() {
        Field upStreamCARequest = ReflectionUtils.findField(UpStreamCARequestEntity.class, "upStreamCARequest", String.class);
        assertThat(upStreamCARequest).isNotNull().withFailMessage("Field 'upStreamCARequest' of type String not found in class UpStreamCARequestEntity");
        ReflectionUtils.makeAccessible(upStreamCARequest);
        return upStreamCARequest;
    }
}
