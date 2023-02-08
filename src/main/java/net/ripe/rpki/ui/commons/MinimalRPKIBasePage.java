package net.ripe.rpki.ui.commons;

import net.ripe.rpki.ui.application.CertificationAdminWicketApplication;
import net.ripe.rpki.ui.commons.menu.NavigationMenuPanel;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.Model;

public abstract class MinimalRPKIBasePage extends WebPage {

    public MinimalRPKIBasePage() {
        addPageTitle();
        add(new NavigationMenuPanel("leftNavigation"));
    }

    public MinimalRPKIBasePage(PageParameters parameters) {
        super(parameters);
        addPageTitle();
        add(new NavigationMenuPanel("leftNavigation"));
    }

    private void addPageTitle() {
        add(new Label("pageTitle", new Model<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                String pageTitle = getPageTitle();
                return StringUtils.isNotBlank(pageTitle) ? pageTitle : "Resource Certification";
            }
        }));
    }

    protected abstract String getPageTitle();

    protected <T> T getBean(Class<T> type) {
        return CertificationAdminWicketApplication.getBean(type);
    }

    protected <T> T getBean(String name, Class<T> type) {
        return CertificationAdminWicketApplication.getBean(name, type);
    }

    protected String pageGAUrl() {
        return null;
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
    }
}
