package net.ripe.rpki.rest.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.pojo.HistoryItem;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.services.system.CaHistoryService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;

@Slf4j
@Scope("prototype")
@RestController
@RequestMapping(path = API_URL_PREFIX + "/{caName}/history", produces = APPLICATION_JSON)
@Tag(name = "/ca/{caName}/history", description = "View command and up-down exchange history of the CA")
public class HistoryService extends AbstractCaRestService {
    private final InternalNamePresenter statsCollectorNames;
    private final CaHistoryService caHistoryService;

    @Autowired
    public HistoryService(InternalNamePresenter statsCollectorNames,
                          CaHistoryService caHistoryService) {
        this.statsCollectorNames = statsCollectorNames;
        this.caHistoryService = caHistoryService;
    }

    @GetMapping
    @Operation(summary = "Get history of a CA")
    public ResponseEntity<List<HistoryItem>> getHistoryForCa(@PathVariable("caName") final CaName caName) {
        log.info("Getting history for CA: {}", caName);

        final List<HistoryItem> items = caHistoryService.getHistoryItems(getCa(CertificateAuthorityData.class, caName)).stream()
                .map(caHistoryItem -> {
                    final String humanizedUserPrincipal = getHumanizedUserPrincipal(caHistoryItem);
                    return new HistoryItem(humanizedUserPrincipal, caHistoryItem);
                }).toList();

        return ok(items);
    }

    private String getHumanizedUserPrincipal(CertificateAuthorityHistoryItem historyItem) {
        String humanizedUserPrincipal = statsCollectorNames.humanizeUserPrincipal(historyItem.getPrincipal());
        return humanizedUserPrincipal != null ? humanizedUserPrincipal : historyItem.getPrincipal();
    }
}
