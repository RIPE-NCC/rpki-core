package net.ripe.rpki.ui.application;

import net.ripe.rpki.server.api.services.command.CertificateAuthorityConcurrentModificationException;
import net.ripe.rpki.ui.admin.SystemStatusPage;
import net.ripe.rpki.ui.audit.CommandListPanel;
import net.ripe.rpki.ui.commons.AdminCertificationBasePage;
import net.ripe.rpki.ui.util.WicketUtils;
import org.apache.wicket.Page;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

public class CertificateAuthorityConcurrentModificationPage extends AdminCertificationBasePage {

    protected CertificateAuthorityConcurrentModificationPage(CertificateAuthorityConcurrentModificationException exception) {
        this(exception, null);
    }

    protected CertificateAuthorityConcurrentModificationPage(CertificateAuthorityConcurrentModificationException exception, Page causingPage) {
        super("Concurrent Modification Error", WicketUtils.caIdToPageParameters(exception.getCertificateAuthorityId()));

        CommandListPanel commandListPanel = new CommandListPanel("conflictingCommandsPanel", exception.getConflictingCommands());
        add(commandListPanel);

        if (causingPage != null) {
            add(new BookmarkablePageLink<Void>("originalPage", causingPage.getClass()));
        } else {
            add(new BookmarkablePageLink<Void>("originalPage", SystemStatusPage.class));
        }
    }

}
