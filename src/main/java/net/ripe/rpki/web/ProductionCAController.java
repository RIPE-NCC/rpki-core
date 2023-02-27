package net.ripe.rpki.web;

import lombok.Value;
import net.ripe.rpki.rest.exception.CaNotFoundException;
import net.ripe.rpki.rest.pojo.HistoryItem;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.CaHistoryService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping(ProductionCAController.PRODUCTION_CA_HISTORY)
public class ProductionCAController {

    public static final String PRODUCTION_CA_HISTORY = "/production-ca-history";
    private final CertificateAuthorityViewService certificateAuthorityViewService;
    private final InternalNamePresenter internalNamePresenter;
    private final RepositoryConfiguration repositoryConfiguration;
    private final CaHistoryService caHistoryService;


    @Inject
    public ProductionCAController(
        CertificateAuthorityViewService certificateAuthorityViewService,
        InternalNamePresenter internalNamePresenter, 
        RepositoryConfiguration repositoryConfiguration,
        CaHistoryService caHistoryService
    ) {
        this.certificateAuthorityViewService = certificateAuthorityViewService;
        this.internalNamePresenter = internalNamePresenter;
        this.repositoryConfiguration = repositoryConfiguration;
        this.caHistoryService = caHistoryService;
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

    @ModelAttribute(name = "historySummary", binding = false)
    public List<HistoryItem> historySummary() {
        return caHistoryService.getHistoryItems(getCa(CertificateAuthorityData.class, CaName.of(repositoryConfiguration.getProductionCaPrincipal()))).stream()
                .map(caHistoryItem -> {
                    final String humanizedUserPrincipal = getHumanizedUserPrincipal(caHistoryItem);
                    return new HistoryItem(humanizedUserPrincipal, caHistoryItem);
                }).collect(Collectors.toList());
    }

    private String getHumanizedUserPrincipal(CertificateAuthorityHistoryItem historyItem) {
        String humanizedUserPrincipal = internalNamePresenter.humanizeUserPrincipal(historyItem.getPrincipal());
        return humanizedUserPrincipal != null ? humanizedUserPrincipal : historyItem.getPrincipal();
    }

    protected <T extends CertificateAuthorityData> T getCa(Class<T> type, CaName caName) {
        return findCa(type, caName).orElseThrow(() -> new CaNotFoundException("certificate authority '" + caName + "' not found"));
    }

    protected <T extends CertificateAuthorityData> Optional<T> findCa(Class<T> type, CaName caName) {
        try {
            return Optional.ofNullable(type.cast(
                    certificateAuthorityViewService.findCertificateAuthorityByName(caName.getPrincipal())
            ));
        } catch (ClassCastException ex) {
            throw new CaNotFoundException("certificate authority '" + caName + "' has incorrect type");
        }
    }

    @GetMapping
    public ModelAndView index() {
        return new ModelAndView("admin/production-ca-history");
    }

    @Value
    public static class UserData {
        String id;
        String name;
        String email;
    }
}
