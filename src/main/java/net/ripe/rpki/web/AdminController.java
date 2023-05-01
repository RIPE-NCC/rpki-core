package net.ripe.rpki.web;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static net.ripe.rpki.server.api.services.background.BackgroundService.BATCH_SIZE_PARAMETER;
import static net.ripe.rpki.server.api.services.background.BackgroundService.FORCE_UPDATE_PARAMETER;

@Controller
@RequestMapping(BaseController.ADMIN_HOME)
@Slf4j
public class AdminController extends BaseController {

    private final Map<String, BackgroundService> backgroundServiceMap;
    private final BackgroundServices backgroundServices;
    private final ProvisioningIdentityViewService provisioningIdentityViewService;

    @Inject
    public AdminController(
        RepositoryConfiguration repositoryConfiguration,
        ActiveNodeService activeNodeService,
        Map<String, BackgroundService> backgroundServiceMap,
        BackgroundServices backgroundServices,
        ProvisioningIdentityViewService provisioningIdentityViewService,
        GitProperties gitProperties
    ) {
        super(repositoryConfiguration, activeNodeService, gitProperties);
        this.backgroundServiceMap = backgroundServiceMap;
        this.backgroundServices = backgroundServices;
        this.provisioningIdentityViewService = provisioningIdentityViewService;
    }

    @ModelAttribute(name = "backgroundServices", binding = false)
    public List<BackgroundServiceData> backgroundServices() {
        return BackgroundServiceData.fromBackgroundServices(backgroundServiceMap);
    }

    @ModelAttribute(name = "provisioningIdentityCertificate", binding = false)
    public ProvisioningIdentityCertificate provisioningIdentityCertificate() {
        return provisioningIdentityViewService.findProvisioningIdentityMaterial();
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
    public ModelAndView activateNode(@Valid ActiveNodeForm node, BindingResult result, RedirectAttributes redirectAttributes) {
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
        @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
        @Nullable @RequestParam(value = BATCH_SIZE_PARAMETER, required = false) Integer batchSize,
        @Nullable @RequestParam(value = FORCE_UPDATE_PARAMETER, required = false) String forceUpdate,
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
                parameters.put(FORCE_UPDATE_PARAMETER, forceUpdate);
            }

            backgroundServices.trigger(serviceId, parameters);

            redirectAttributes.addFlashAttribute("success", String.format("Scheduled service '%s' for execution", service.getName()));
        }
        return referrer == null ? redirectToIndex() : new RedirectView(referrer);
    }

    @GetMapping(
        path = {"/provisioning-identity-certificate.cer"},
        produces = "application/pkix-cert"
    )
    public ResponseEntity<byte[]> getProvisioningIdentityCertificate() {
        ProvisioningIdentityCertificate provisioningIdentityMaterial = provisioningIdentityViewService.findProvisioningIdentityMaterial();
        if (provisioningIdentityMaterial == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("provisioning-identity-certificate.cer").build().toString())
                .body(provisioningIdentityMaterial.getEncoded());
    }

}
