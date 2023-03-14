package net.ripe.rpki.web;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.ripencc.ui.daemon.health.HealthService;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.inject.Inject;
import java.util.Map;

@Controller
@RequestMapping(HealthCheckController.HEALTH_CHECK)
public class HealthCheckController extends BaseController {
    public static final String HEALTH_CHECK = "/admin/health-check";
    private final HealthService healthService;

    @Inject
    public HealthCheckController(
            RepositoryConfiguration repositoryConfiguration,
            ActiveNodeService activeNodeService,
            HealthService healthService
    ) {
        super(repositoryConfiguration, activeNodeService);
        this.healthService = healthService;
    }

    @ModelAttribute(name = "healthChecks", binding = false)
    public Map<String, Health.Status> healthChecks() {
        return healthService.getHealthChecks().getBody();
    }

    @GetMapping
    public ModelAndView index() {
        return new ModelAndView("admin/health-check", "activeNodeForm", new ActiveNodeForm(activeNodeService.getActiveNodeName()));
    }
}
