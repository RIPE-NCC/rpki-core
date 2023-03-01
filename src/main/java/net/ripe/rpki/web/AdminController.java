package net.ripe.rpki.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static net.ripe.rpki.server.api.services.background.BackgroundService.BATCH_SIZE_PARAMETER;
import static net.ripe.rpki.server.api.services.background.BackgroundService.FORCE_UPDATE_PARAMETER;

@Controller
@RequestMapping(AdminController.ADMIN_HOME)
public class AdminController {

    public static final String ADMIN_HOME = "/admin";

    private final RepositoryConfiguration repositoryConfiguration;
    private final ActiveNodeService activeNodeService;
    private final Map<String, BackgroundService> backgroundServiceMap;
    private final BackgroundServices backgroundServices;

    @Inject
    public AdminController(RepositoryConfiguration repositoryConfiguration, ActiveNodeService activeNodeService, Map<String, BackgroundService> backgroundServiceMap, BackgroundServices backgroundServices) {
        this.repositoryConfiguration = repositoryConfiguration;
        this.activeNodeService = activeNodeService;
        this.backgroundServiceMap = backgroundServiceMap;
        this.backgroundServices = backgroundServices;
    }

    @ModelAttribute(name = "currentUser", binding = false)
    public UserData currentUser(@AuthenticationPrincipal Object user) {
        if (user instanceof OAuth2User) {
            OAuth2User oAuth2User = (OAuth2User) user;
            return new UserData(oAuth2User.getName(), oAuth2User.getAttribute("name"), oAuth2User.getAttribute("email"));
        } else {
            String id = String.valueOf(user);
            return new UserData(id, id, null);
        }
    }

    @ModelAttribute(name = "coreConfiguration", binding = false)
    public CoreConfigurationData coreConfigurationData() {
        return new CoreConfigurationData(repositoryConfiguration, activeNodeService);
    }

    @ModelAttribute(name = "backgroundServices", binding = false)
    public List<BackgroundServiceData> backgroundServices() {
        return backgroundServiceMap.entrySet().stream()
            .map(entry -> new BackgroundServiceData(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(s -> s.name))
            .collect(Collectors.toList());
    }

    @GetMapping
    public ModelAndView index() {
        return new ModelAndView("admin/index", "activeNodeForm", new ActiveNodeForm(activeNodeService.getActiveNodeName()));
    }

    @GetMapping({"/index.html"})
    public RedirectView redirectToIndex() {
        boolean contextRelative = true;
        return new RedirectView(ADMIN_HOME, contextRelative);
    }

    @PostMapping({"/activate-node"})
    public ModelAndView activateNode(@Valid AdminController.ActiveNodeForm node, BindingResult result, RedirectAttributes redirectAttributes) {
        FieldError nameError = result.getFieldError("name");
        if (nameError != null) {
            return new ModelAndView("admin/index", HttpStatus.BAD_REQUEST)
                .addObject("error", String.format("Active node name %s", nameError.getDefaultMessage()))
                .addObject("activeNodeForm", node);
        }

        activeNodeService.setActiveNodeName(node.name.trim());

        redirectAttributes.addFlashAttribute("success", String.format("Active node set to '%s'", activeNodeService.getActiveNodeName()));
        return new ModelAndView(redirectToIndex());
    }

    @PostMapping({"/services/{serviceId}"})
    public RedirectView runBackgroundService(
        @PathVariable("serviceId") String serviceId,
        @Nullable @RequestParam(value = BATCH_SIZE_PARAMETER, required = false) Integer batchSize,
        @Nullable @RequestParam(value = FORCE_UPDATE_PARAMETER, required = false) Boolean forceUpdate,
        RedirectAttributes redirectAttributes
    ) {
        BackgroundService service = backgroundServiceMap.get(serviceId);
        if (service == null) {
            redirectAttributes.addFlashAttribute("error", "Service not found");
        } else {
            Map<String, String> parameters = new TreeMap<>();
            if (batchSize != null) {
                parameters.put(BATCH_SIZE_PARAMETER, batchSize.toString());
            }
            if (forceUpdate != null) {
                parameters.put(FORCE_UPDATE_PARAMETER, forceUpdate.toString());
            }

            backgroundServices.trigger(serviceId, parameters);

            redirectAttributes.addFlashAttribute("success", String.format("Scheduled service '%s' for execution", service.getName()));
        }
        return redirectToIndex();
    }

    @Value
    public static class UserData {
        String id;
        String name;
        String email;
    }

    @Value @AllArgsConstructor
    public static class CoreConfigurationData {
        String localRepositoryDirectory;
        String publicRepositoryUri;
        String activeNodeName;
        String currentNodeName;

        CoreConfigurationData(RepositoryConfiguration repositoryConfiguration, ActiveNodeService activeNodeService) {
            this.localRepositoryDirectory = repositoryConfiguration.getLocalRepositoryDirectory().getAbsolutePath();
            this.publicRepositoryUri = repositoryConfiguration.getPublicRepositoryUri().toASCIIString();
            this.activeNodeName = activeNodeService.getActiveNodeName();
            this.currentNodeName = activeNodeService.getCurrentNodeName();
        }
    }

    @Value @AllArgsConstructor
    public static class BackgroundServiceData {
        String id;
        String name;
        String status;
        boolean active;
        boolean waitingOrRunning;
        Map<String, String> supportedParameters;

        BackgroundServiceData(String id, BackgroundService backgroundService) {
            this.id = id;
            this.name = backgroundService.getName();
            this.status = backgroundService.getStatus();
            this.active = backgroundService.isActive();
            this.waitingOrRunning = backgroundService.isWaitingOrRunning();
            this.supportedParameters = backgroundService.supportedParameters();
        }
    }

    @Data @AllArgsConstructor
    public static class ActiveNodeForm {
        @NotBlank
        // Regex taken from https://stackoverflow.com/a/106223
        @Pattern(
            regexp = "\\s*(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\\s*",
            message = "must be a valid hostname"
        )
        String name;
    }
}
