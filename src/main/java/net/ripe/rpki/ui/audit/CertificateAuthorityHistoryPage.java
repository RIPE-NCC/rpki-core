package net.ripe.rpki.ui.audit;

import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import org.apache.commons.lang.Validate;
import org.apache.wicket.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.util.ArrayList;
import java.util.List;

public class CertificateAuthorityHistoryPage extends AdminCertificationBasePage {
    @SpringBean
    private CertificateAuthorityViewService caViewService;

    public CertificateAuthorityHistoryPage(PageParameters parameters) {
        super("History", parameters);
        Validate.isTrue(hasCurrentCertificateAuthorityOfAnyType(), "no current CA");
        add(new CommandListPanel("commandListPanel", findCommandHistory()));
    }

    private List<CertificateAuthorityHistoryItem> findCommandHistory() {

        List<CertificateAuthorityHistoryItem> historyItems = new ArrayList<>();
        historyItems.addAll(caViewService.findMostRecentCommandsForCa(getCurrentCertificateAuthority().getId()));
        historyItems.addAll(caViewService.findMostRecentMessagesForCa(getCurrentCertificateAuthority().getUuid()));

        historyItems.sort((object1, object2) -> object2.getExecutionTime().compareTo(object1.getExecutionTime()));

        return historyItems;
    }
}
