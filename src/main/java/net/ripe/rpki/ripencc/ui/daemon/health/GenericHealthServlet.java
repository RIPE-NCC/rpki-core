package net.ripe.rpki.ripencc.ui.daemon.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class GenericHealthServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(GenericHealthServlet.class);

    protected WebApplicationContext springContext;

    @Override
    public void init() {
        springContext = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final Map<String, Health.Status> statuses = Health.statuses(getHealthChecks());
        final String json = Health.toJson(statuses);
        int httpCode = Health.httpCode(statuses);
        if (httpCode != 200) {
            log.warn("Health check servlet is called, result is {}", json);
        }
        response.setStatus(httpCode);
        response.setContentType("application/json");
        response.getWriter().write(json);
    }

    protected abstract List<Health.Check> getHealthChecks();
}
