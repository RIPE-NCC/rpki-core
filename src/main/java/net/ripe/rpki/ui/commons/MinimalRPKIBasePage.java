package net.ripe.rpki.ui.commons;

import net.ripe.rpki.ui.commons.menu.NavigationMenuPanel;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.springframework.core.env.Environment;

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

    @SpringBean
    private Environment environment;

    protected String pageGAUrl() {
        return null;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
    }
}
