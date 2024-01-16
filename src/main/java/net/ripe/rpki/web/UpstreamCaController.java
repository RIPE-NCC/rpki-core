package net.ripe.rpki.web;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorRequestSerializer;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorResponseSerializer;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;

@Controller
@RequestMapping(BaseController.ADMIN_HOME)
@Slf4j
public class UpstreamCaController extends BaseController {

    public static final String UPSTREAM_CA = "upstream-ca";
    public static final String PAGE_TYPE = "pageType";
    public static final String ACA_KEY_STATUS = "acaKeyStatus";
    public static final String ADMIN_INDEX_PAGE = "admin/index";
    public static final String ACTIVE_NODE_FORM = "activeNodeForm";

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final CommandService commandService;
    private final AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean;
    private final Map<String, BackgroundService> backgroundServiceMap;

    @Inject
    public UpstreamCaController(RepositoryConfiguration repositoryConfiguration,
                                ActiveNodeService activeNodeService,
                                CertificateAuthorityViewService certificateAuthorityViewService,
                                CommandService commandService,
                                AllCaCertificateUpdateServiceBean allCaCertificateUpdateServiceBean,
                                Map<String, BackgroundService> backgroundServiceMap,
                                GitProperties gitProperties) {
        super(repositoryConfiguration, activeNodeService, gitProperties);
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.commandService = commandService;
        this.allCaCertificateUpdateServiceBean = allCaCertificateUpdateServiceBean;
        this.backgroundServiceMap = backgroundServiceMap;
    }

    @ModelAttribute(name = "backgroundServices", binding = false)
    public List<BackgroundServiceData> backgroundServices() {
        return BackgroundServiceData.fromBackgroundServices(backgroundServiceMap);
    }

    @GetMapping({"/upstream-ca"})
    public ModelAndView upstreamCa() {
        final Map<String, Object> model = new HashMap<>();
        return (ModelAndView) withAllResourcesCa(allResourcesCa -> {
            var overallStatus = overallKeyPairLifeCyclePhase(allResourcesCa);
            model.put(ACA_KEY_STATUS, overallStatus.map(x -> x.name().toLowerCase(Locale.ROOT)).orElse("new"));
            if (allResourcesCa.getTrustAnchorRequest() == null) {
                model.put(PAGE_TYPE, "create-request");
            } else {
                // Do not show any extra key life-cycle buttons to avoid
                // clicking wrong button when uploading TA response.
                model.put(ACA_KEY_STATUS, "none");
                model.put(PAGE_TYPE, "download-request");
                model.put("requestFileName", getRequestFileName(allResourcesCa.getTrustAnchorRequest()));
            }
            return new ModelAndView("admin/upstream-ca", model);
        });
    }

    @PostMapping({"/create-sign-request"})
    public RedirectView generateSigningRequest() {
        return (RedirectView) withAllResourcesCa(allResourcesCa -> {
            commandService.execute(new AllResourcesCaResourcesCommand(allResourcesCa.getVersionedId()));
            return new RedirectView(UPSTREAM_CA, true);
        });
    }

    @GetMapping({"/download-sign-request"})
    public Object downloadSignRequest() {
        final CertificateAuthorityData allResourcesCa = getAllResourcesCa();
        if (allResourcesCa == null || allResourcesCa.getTrustAnchorRequest() == null) {
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView(ADMIN_INDEX_PAGE, HttpStatus.NOT_FOUND)
                    .addObject("error", "All Resources CA or signing request do not exist.")
                    .addObject("activeNodeForm", node);
        }

        final TrustAnchorRequest taRequest = allResourcesCa.getTrustAnchorRequest();
        final byte[] bytes = new TrustAnchorRequestSerializer().serialize(taRequest).getBytes(UTF_8);

        final HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_XML);
        responseHeaders.setContentLength(bytes.length);
        responseHeaders.setContentDisposition(ContentDisposition
                    .attachment().filename(getRequestFileName(taRequest)).build());
        return new ResponseEntity<>(bytes, responseHeaders, HttpStatus.OK);
    }

    @PostMapping({"/upload-sign-response"})
    public Object uploadSignResponse(@RequestParam("response") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            final String responseXml = new String(file.getBytes(), UTF_8);
            final TrustAnchorResponse response = new TrustAnchorResponseSerializer().deserialize(responseXml);

            final X500Principal allResourcesCaName = repositoryConfiguration.getAllResourcesCaPrincipal();
            final CertificateAuthorityData allResourcesCa = certificateAuthorityViewService.findCertificateAuthorityByName(allResourcesCaName);
            commandService.execute(new ProcessTrustAnchorResponseCommand(allResourcesCa.getVersionedId(), response));
            allCaCertificateUpdateServiceBean.execute(Collections.emptyMap());

            redirectAttributes.addFlashAttribute("success", "Successfully uploaded " + file.getName());
            return new RedirectView(UPSTREAM_CA, true);
        } catch (Exception e) {
            log.error("Could not upload response", e);
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView("admin/index", HttpStatus.NOT_FOUND)
                .addObject("error", "Could not upload/process response, " + e.getMessage())
                .addObject("activeNodeForm", node);
        }
    }

    @PostMapping({"/revoke-old-aca-key"})
    public Object revokeOldAcaKey() {
        return withAllResourcesCa(allResourcesCa ->
                ifNoTaRequest(allResourcesCa, () -> {
                    commandService.execute(new KeyManagementRevokeOldKeysCommand(allResourcesCa.getVersionedId()));
                    return new RedirectView(UPSTREAM_CA, true);
                }));
    }

    @PostMapping({"/activate-pending-aca-key"})
    public Object activateAcaPendingKey() {
        return withAllResourcesCa(allResourcesCa ->
                ifNoTaRequest(allResourcesCa, () -> {
                    commandService.execute(KeyManagementActivatePendingKeysCommand.manualActivationCommand(allResourcesCa.getVersionedId()));
                    allCaCertificateUpdateServiceBean.execute(Collections.emptyMap());
                    return new RedirectView(UPSTREAM_CA, true);
                }));
    }

    @PostMapping({"/initiate-rolling-aca-key"})
    public Object initiateRollingAcaKey() {
        return withAllResourcesCa(allResourcesCa ->
                ifNoTaRequest(allResourcesCa, () -> {
                    commandService.execute(new KeyManagementInitiateRollCommand(allResourcesCa.getVersionedId(), 0));
                    return new RedirectView(UPSTREAM_CA, true);
                }));
    }

    private Object withAllResourcesCa(Function<? super ManagedCertificateAuthorityData, Object> f) {
        final CertificateAuthorityData allResourcesCa = getAllResourcesCa();
        if (allResourcesCa == null) {
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView("admin/index", HttpStatus.BAD_REQUEST)
                .addObject("error", "All resources CA does not exist")
                .addObject("activeNodeForm", node);
        } else if (allResourcesCa.getType() == CertificateAuthorityType.ALL_RESOURCES && allResourcesCa instanceof ManagedCertificateAuthorityData) {
            return f.apply((ManagedCertificateAuthorityData) allResourcesCa);
        } else {
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView("admin/index", HttpStatus.BAD_REQUEST)
                .addObject("error", "All resources CA has wrong type")
                .addObject(ACTIVE_NODE_FORM, node);
        }
    }

    private Object ifNoTaRequest(CertificateAuthorityData allResourcesCa, Supplier<Object> f) {
        if (allResourcesCa.getTrustAnchorRequest() != null) {
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView("admin/index", HttpStatus.BAD_REQUEST)
                    .addObject("error", "All resources CA already has a TA request, it must be processed first.")
                    .addObject("activeNodeForm", node);
        }
        return f.get();
    }

    private static String getRequestFileName(TrustAnchorRequest taRequest) {
        String createdAt = new DateTime(taRequest.getCreationTimestamp(), DateTimeZone.UTC).toString("yyyyMMdd-HHmmss");
        return "request-" + createdAt + ".xml";
    }

    private CertificateAuthorityData getAllResourcesCa() {
        return certificateAuthorityViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
    }

    private Optional<KeyPairStatus> overallKeyPairLifeCyclePhase(ManagedCertificateAuthorityData allResourcesCa) {
        final List<KeyPairData> keys = allResourcesCa.getKeys();
        return keyWithStatus(keys, KeyPairStatus.OLD)
            .or(() -> keyWithStatus(keys, KeyPairStatus.PENDING))
            .or(() -> keyWithStatus(keys, KeyPairStatus.CURRENT));
    }

    private Optional<KeyPairStatus> keyWithStatus(Collection<KeyPairData> keys, KeyPairStatus expectedStatus) {
        return keys.stream()
            .map(KeyPairData::getStatus)
            .filter(expectedStatus::equals)
            .findFirst();
    }
}
