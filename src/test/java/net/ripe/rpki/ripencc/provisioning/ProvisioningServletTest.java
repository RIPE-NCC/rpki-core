package net.ripe.rpki.ripencc.provisioning;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.ripe.rpki.rest.security.RequestEntitySizeLimiterServletFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProvisioningServletTest {
    private static final String CONTENT_TYPE = "application/rpki-updown";
    private final String fakeUserAgent = "UA";

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @Mock
    private ProvisioningService service;

    private ProvisioningServlet servlet;

    @Mock
    private ProvisioningMetricsService provisioningMetrics;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setContentType(CONTENT_TYPE);

        response = new MockHttpServletResponse();

        servlet = new ProvisioningServlet(service, provisioningMetrics);
    }

    @Test
    public void shouldReadPostData() throws Exception {
        // given
        byte[] requestData = {1, 2};
        byte[] responseData = {3, 4};
        request.setContent(requestData);
        request.addHeader("user-agent", fakeUserAgent);

        when(service.processRequest(requestData, fakeUserAgent)).thenReturn(responseData);

        // when
        servlet.doPost(request, response);

        // then
        assertThat(response.getContentAsByteArray()).isEqualTo(responseData);
    }

    @Test
    public void should_translate_RequestEntityTooLargeException_to_http_error_response() throws Exception {
        request.setContent(new byte[RequestEntitySizeLimiterServletFilter.MAX_REQUEST_SIZE + 1]);

        new RequestEntitySizeLimiterServletFilter().doFilter(
            request,
            response,
            (req, res) -> servlet.doPost((HttpServletRequest) req, (HttpServletResponse) res)
        );

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    public void shouldTranslateProvisioningExceptionToHttpErrorResponse() throws Exception {
        ProvisioningException provisioningException = new ProvisioningException.UnknownProvisioningUrl();
        request.setContent(new byte[] {});
        request.addHeader("user-agent", fakeUserAgent);

        when(service.processRequest(any(byte[].class), eq(fakeUserAgent))).thenThrow(provisioningException);

        servlet.doPost(request, response);

        then(provisioningMetrics).should().trackProvisioningExceptionCause(provisioningException);

        assertThat(response.getStatus()).isEqualTo(provisioningException.getHttpStatusCode());
        assertThat(response.getErrorMessage()).isEqualTo(provisioningException.getDescription());
    }

    @Test
    public void should_not_restrict_rpki_updown_content_type_to_support_rpkid() throws Exception {
        when(service.processRequest(any(), any())).thenReturn(new byte[]{0xD, 0xE, 0xA, 0xD, 0xB, 0xE, 0xE, 0xF});
        request.setContentType("text/html");
        servlet.doPost(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
