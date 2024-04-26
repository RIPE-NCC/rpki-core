package net.ripe.rpki.ripencc.provisioning;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.exception.RequestEntityTooLargeException;
import org.apache.commons.io.IOUtils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class ProvisioningServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String CONTENT_TYPE = "application/rpki-updown";

    private final ProvisioningService provisioningService;

    private final ProvisioningMetricsService provisioningMetrics;

    public ProvisioningServlet(ProvisioningService service, ProvisioningMetricsService provisioningMetrics) {
        this.provisioningService = service;
        this.provisioningMetrics = provisioningMetrics;
    }

    @Override
    @SuppressWarnings("java:S1989") // Ignore Sonar warnings about uncaught IOExceptions
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // https://datatracker.ietf.org/doc/html/rfc6492#section-3
        // A message exchange commences with the client initiating an HTTP POST
        // with content type of "application/rpki-updown" and the message object
        // as the body.
        if(!CONTENT_TYPE.equalsIgnoreCase(req.getContentType())) {
            log.warn("Got unsupported content-type: {} from {}/{}. Will try to process anyway.",
                    req.getContentType(), req.getRemoteAddr(), req.getHeader("User-Agent"));
        }

        try {
            byte[] request = IOUtils.toByteArray(req.getInputStream());
            byte[] response = provisioningService.processRequest(request);

            // The server's response is similarly an HTTP response,
            //   with the message object carried as the body of the response and with
            //   a response content type of "application/rpki-updown"
            resp.setContentType(CONTENT_TYPE);
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.write(response);
        } catch (RequestEntityTooLargeException e) {
            log.warn("provisioning error: entity too large");
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        } catch (ProvisioningException e) {
            provisioningMetrics.trackProvisioningExceptionCause(e);
            // content-type not set for HTTP 400/503, non-CMS error responses
            log.warn("provisioning error, HTTP {}: {}", e.getHttpStatusCode(), e.getDescription());
            resp.sendError(e.getHttpStatusCode(), e.getDescription());
        }
    }
}
