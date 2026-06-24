package net.ripe.rpki.web;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.commands.AdminDeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.DelegatedCa;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequestMapping(RevokeDelegatedCaController.REVOKE_DELEGATED_CA)
@Slf4j
public class RevokeDelegatedCaController extends BaseController {

    public static final String REVOKE_DELEGATED_CA = "/admin/revoke-delegated-ca";
    public static final String ERROR = "error";

    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final CommandService commandService;
    private final String prometheusBaseUrl;

    @Inject
    public RevokeDelegatedCaController(
            CertificateAuthorityViewService certificateAuthorityViewService,
            CommandService commandService,
            RepositoryConfiguration repositoryConfiguration,
            ActiveNodeService activeNodeService,
            GitProperties gitProperties,
            @Value("${prometheus.base.url}") String prometheusBaseUrl
    ) {
        super(repositoryConfiguration, activeNodeService, gitProperties);
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.commandService = commandService;
        this.prometheusBaseUrl = prometheusBaseUrl;
    }

    @GetMapping
    public ModelAndView index(@RequestParam(value = "caName", required = false) String caNameText) {
        var searchSubmitted = caNameText != null;
        var searchTerm = caNameText == null ? "" : caNameText.trim();
        var prometheusLink = Optional.empty();

        var foundDelegatedCa = Optional.<DelegatedCa>empty();
        if (searchSubmitted && StringUtils.hasText(searchTerm)) {
            var normalizedSearchTerm = searchTerm.toLowerCase(Locale.ROOT);
            foundDelegatedCa = certificateAuthorityViewService
                    .findDelegatedCas().stream()
                    .filter(ca -> ca.caName().toLowerCase(Locale.ROOT).equals(normalizedSearchTerm))
                    .findFirst();
        }

        if(foundDelegatedCa.isPresent()) {
            var fragment = UriUtils.encodeFragment("query?g0.expr=camon_delegated_ca_functional{CA=\"" + foundDelegatedCa.get().caName() + "\"}", StandardCharsets.UTF_8);
            prometheusLink = Optional.of(this.prometheusBaseUrl + fragment);
        }

        return new ModelAndView("admin/revoke-delegated-ca")
                .addObject("caName", searchTerm)
                .addObject("searchSubmitted", searchSubmitted)
                .addObject("foundDelegatedCa", foundDelegatedCa)
                .addObject("prometheusLink", prometheusLink);
    }

    @PostMapping
    public Object revoke(@RequestParam(value = "caName") String caName,
                         RedirectAttributes redirectAttributes,
                         @ModelAttribute("currentUser") UserData currentUser) {
        try {
            var parsedCaName = CaName.parse(caName);
            var ca = certificateAuthorityViewService.findCertificateAuthorityByName(parsedCaName.getPrincipal());
            if (ca == null) {
                redirectAttributes.addFlashAttribute(ERROR, "CA '" + parsedCaName + "' doesn't exist.");
                return new RedirectView(REVOKE_DELEGATED_CA, true);
            }
            if (CertificateAuthorityType.NONHOSTED.equals(ca.getType())) {
                commandService.execute(new AdminDeleteCertificateAuthorityCommand(ca.getVersionedId(), ca.getName(), currentUser.getName()));
                redirectAttributes.addFlashAttribute("success", "Delegated CA '" + parsedCaName + "' has been revoked.");
                log.info("User {} revoked delegated (non-hosted) CA: {}", currentUser.getName(), parsedCaName);
            } else {
                redirectAttributes.addFlashAttribute(ERROR, "CA '" + parsedCaName + "' is not a delegated CA.");
                return new RedirectView(REVOKE_DELEGATED_CA, true);
            }
        } catch (CaName.BadCaNameException e) {
            redirectAttributes.addFlashAttribute(ERROR, "Invalid CA name format: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to revoke delegated CA '{}'", caName, e);
            redirectAttributes.addFlashAttribute(ERROR, "Failed to revoke CA '" + caName + "': " + e.getMessage());
        }
        return new RedirectView(REVOKE_DELEGATED_CA, true);
    }
}
