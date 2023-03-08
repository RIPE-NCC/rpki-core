package net.ripe.rpki.web;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.commons.ta.domain.response.TrustAnchorResponse;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorRequestSerializer;
import net.ripe.rpki.commons.ta.serializers.TrustAnchorResponseSerializer;
import net.ripe.rpki.server.api.commands.AllResourcesCaResourcesCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.KeyPairData;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

@Controller
@RequestMapping(BaseController.ADMIN_HOME)
@Slf4j
public class UpstreamCaController extends BaseController {

    public static final String UPSTREAM_CA = "upstream-ca";
    public static final String PAGE_TYPE = "pageType";
    public static final String ACA_KEY_STATUS = "acaKeyStatus";

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
                                Map<String, BackgroundService> backgroundServiceMap) {
        super(repositoryConfiguration, activeNodeService);
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
            KeyPairStatus overallStatus = overallKeyPairLifeCyclePhase((ManagedCertificateAuthorityData)allResourcesCa);
            model.put(ACA_KEY_STATUS, overallStatus.name().toLowerCase(Locale.ROOT));
            if (allResourcesCa.getTrustAnchorRequest() == null) {
                model.put(PAGE_TYPE, "create-request");
            } else {
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
            return new ModelAndView("admin/index", HttpStatus.NOT_FOUND)
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
    public RedirectView revokeOldAcaKey() {
        return (RedirectView) withAllResourcesCa(allResourcesCa -> {
            commandService.execute(new KeyManagementRevokeOldKeysCommand(allResourcesCa.getVersionedId()));
            return new RedirectView(UPSTREAM_CA, true);
        });
    }

    @PostMapping({"/activate-pending-aca-key"})
    public RedirectView activateAcaPendingKey() {
        return (RedirectView) withAllResourcesCa(allResourcesCa -> {
            commandService.execute(KeyManagementActivatePendingKeysCommand.manualActivationCommand(allResourcesCa.getVersionedId()));
            allCaCertificateUpdateServiceBean.execute(Collections.emptyMap());
            return new RedirectView(UPSTREAM_CA, true);
        });
    }

    @PostMapping({"/initiate-rolling-aca-key"})
    public RedirectView initiateRollingAcaKey() {
        return (RedirectView) withAllResourcesCa(allResourcesCa -> {
            commandService.execute(new KeyManagementInitiateRollCommand(allResourcesCa.getVersionedId(), 0));
            return new RedirectView(UPSTREAM_CA, true);
        });
    }

    private Object withAllResourcesCa(Function<CertificateAuthorityData, Object> f) {
        final CertificateAuthorityData allResourcesCa = getAllResourcesCa();
        if (allResourcesCa == null) {
            final ActiveNodeForm node = new ActiveNodeForm(activeNodeService.getActiveNodeName());
            return new ModelAndView("admin/index", HttpStatus.BAD_REQUEST)
                .addObject("error", "All resources CA does not exist")
                .addObject("activeNodeForm", node);
        } else {
            return f.apply(allResourcesCa);
        }
    }

    private static String getRequestFileName(TrustAnchorRequest taRequest) {
        String createdAt = new DateTime(taRequest.getCreationTimestamp(), DateTimeZone.UTC).toString("yyyyMMdd-HHmmss");
        return "request-" + createdAt + ".xml";
    }

    private CertificateAuthorityData getAllResourcesCa() {
        return certificateAuthorityViewService.findCertificateAuthorityByName(repositoryConfiguration.getAllResourcesCaPrincipal());
    }

    private KeyPairStatus overallKeyPairLifeCyclePhase(ManagedCertificateAuthorityData allResourcesCa) {
        final List<KeyPairData> keys = allResourcesCa.getKeys();
        return keyWithStatus(keys, KeyPairStatus.OLD)
            .or(() -> keyWithStatus(keys, KeyPairStatus.PENDING))
            .or(() -> keyWithStatus(keys, KeyPairStatus.CURRENT))
            .orElse(KeyPairStatus.NEW);
    }

    private Optional<KeyPairStatus> keyWithStatus(Collection<KeyPairData> keys, KeyPairStatus expectedStatus) {
        return keys.stream()
            .map(KeyPairData::getStatus)
            .filter(expectedStatus::equals)
            .findFirst();
    }
}
