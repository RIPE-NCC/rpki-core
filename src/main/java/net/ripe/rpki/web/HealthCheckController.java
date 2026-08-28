package net.ripe.rpki.web;

import jakarta.inject.Inject;
import lombok.NonNull;
import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping(HealthCheckController.HEALTH_CHECK)
public class HealthCheckController extends BaseController {
    public static final String HEALTH_CHECK = "/admin/health-check";
    private final List<Health.Check> healthchecks;

    @Inject
    public HealthCheckController(
            RepositoryConfiguration repositoryConfiguration,
            ActiveNodeService activeNodeService,
            @NonNull List<Health.Check> healthchecks,
            GitProperties gitProperties
    ) {
        super(repositoryConfiguration, activeNodeService, gitProperties);
        this.healthchecks = healthchecks;
    }

    @ModelAttribute(name = "healthChecks", binding = false)
    public Map<String, Health.Status> healthChecks(Model model) {
        try {
            return Health.statuses(healthchecks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            model.addAttribute("error", "Interrupted while checking health statuses");
        } catch (ExecutionException e) {
            model.addAttribute("error", "Failed to execute status checks: " + e.getMessage());
        }
        return Map.of();
    }

    @GetMapping
    public ModelAndView index() {
        return new ModelAndView("admin/health-check", "activeNodeForm", new ActiveNodeForm(activeNodeService.getActiveNodeName()));
    }
}
