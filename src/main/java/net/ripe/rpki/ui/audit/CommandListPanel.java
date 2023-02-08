package net.ripe.rpki.ui.audit;

import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.basic.MultiLineLabel;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.util.List;

import static net.ripe.rpki.ui.application.CertificationAdminWicketApplication.getBean;


@SuppressWarnings("deprecation")
public class CommandListPanel extends Panel {

    private static final long serialVersionUID = 1L;

    protected static DateTimeFormatter executionTimeFormat = new DateTimeFormatterBuilder()
            .appendYear(4, 4).appendLiteral('-').appendMonthOfYear(1)
            .appendLiteral('-').appendDayOfMonth(1)
            .appendLiteral(' ').appendHourOfDay(1)
            .appendLiteral(':').appendMinuteOfHour(2)
            .appendLiteral(':').appendSecondOfMinute(2)
            .toFormatter();

    public CommandListPanel(String id, List<? extends CertificateAuthorityHistoryItem> commands) {
        super(id);


        ListView<CertificateAuthorityHistoryItem> listView = new ListView<CertificateAuthorityHistoryItem>("commandList", commands) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<CertificateAuthorityHistoryItem> item) {
                CertificateAuthorityHistoryItem command = item.getModelObject();
                item.add(new Label("executionTime", executionTimeFormat.print(command.getExecutionTime())));
                item.add(new Label("executionTimestamp", String.valueOf(command.getExecutionTime().getMillis())));

                final InternalNamePresenter resolver = getBean(InternalNamePresenter.class);
                String usernameFromSSO = resolver.humanizeUserPrincipal(command.getPrincipal());
                String username = usernameFromSSO == null ? command.getPrincipal() : usernameFromSSO;
                item.add(new Label("principal", username));

                item.add(new MultiLineLabel("summary", command.getSummary()));
            }
        };
        add(listView);
    }
}
