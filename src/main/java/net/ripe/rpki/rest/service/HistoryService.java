package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.pojo.HistoryItem;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/history", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/history", description = "View command and up-down exchange history of the CA")
public class HistoryService extends AbstractCaRestService {
    private final InternalNamePresenter statsCollectorNames;
    private final CertificateAuthorityViewService certificateAuthorityViewService;

    @Autowired
    public HistoryService(InternalNamePresenter statsCollectorNames,
                          CertificateAuthorityViewService certificateAuthorityViewService) {
        super(false, true);
        this.statsCollectorNames = statsCollectorNames;
        this.certificateAuthorityViewService = certificateAuthorityViewService;
    }

    @GetMapping
    @Operation(summary = "Get history of a CA")
    public ResponseEntity<List<HistoryItem>> getHistoryForCa(@PathVariable("caName") final String caName) {
        log.info("Getting history for CA: {}", getRawCaName());

        final List<HistoryItem> items = getHistoryItems(this.getCaId()).stream()
                .map(caHistoryItem -> {
                    final String humanizedUserPrincipal = getHumanizedUserPrincipal(caHistoryItem);
                    return new HistoryItem(humanizedUserPrincipal, caHistoryItem);
                }).collect(Collectors.toList());

        return ok(items);
    }

    private String getHumanizedUserPrincipal(CertificateAuthorityHistoryItem cahistoryItem) {
        String humanizedUserPrincipal = statsCollectorNames.humanizeUserPrincipal(cahistoryItem.getPrincipal());
        return humanizedUserPrincipal != null ? humanizedUserPrincipal : cahistoryItem.getPrincipal();
    }

    private List<CertificateAuthorityHistoryItem> getHistoryItems(Long caId) {
        final CertificateAuthorityData certificateAuthority = certificateAuthorityViewService.findCertificateAuthority(caId);
        List<CertificateAuthorityHistoryItem> historyItems = new ArrayList<>();
        historyItems.addAll(certificateAuthorityViewService.findMostRecentCommandsForCa(certificateAuthority.getId()));
        historyItems.addAll(certificateAuthorityViewService.findMostRecentMessagesForCa(certificateAuthority.getUuid()));

        historyItems.sort((object1, object2) -> object2.getExecutionTime().compareTo(object1.getExecutionTime()));
        return historyItems;
    }

}
