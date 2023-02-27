package net.ripe.rpki.services.impl;

import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.services.system.CaHistoryService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CaHistoryServiceBean implements CaHistoryService {
    private final CertificateAuthorityViewService certificateAuthorityViewService;

    public CaHistoryServiceBean(CertificateAuthorityViewService certificateAuthorityViewService) {
        this.certificateAuthorityViewService = certificateAuthorityViewService;
    }
    @Override
    public List<CertificateAuthorityHistoryItem> getHistoryItems(CertificateAuthorityData certificateAuthority) {
        List<CertificateAuthorityHistoryItem> historyItems = new ArrayList<>();
        historyItems.addAll(certificateAuthorityViewService.findMostRecentCommandsForCa(certificateAuthority.getId()));
        historyItems.addAll(certificateAuthorityViewService.findMostRecentMessagesForCa(certificateAuthority.getUuid()));

        historyItems.sort((object1, object2) -> object2.getExecutionTime().compareTo(object1.getExecutionTime()));
        return historyItems;
    }
}