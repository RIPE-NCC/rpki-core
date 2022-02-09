package net.ripe.rpki.ui.admin;

import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.apache.wicket.authentication.AuthenticatedWebSession;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.value.ValueMap;


public class AdminLoginPage extends WebPage {

    @SpringBean
    private CertificateAuthorityViewService caViewService;

    @SpringBean
    private CommandService commandService;


    public AdminLoginPage()
    {
        // Create feedback panel and add to page
        add(new FeedbackPanel("feedback"));

        // Add sign-in form to page
        add(new SignInForm("signInForm"));
    }

    /**
     * Sign in form
     */
    public final class SignInForm extends Form<Void>
    {
        private static final String PASSWORD = "password";

        private final ValueMap properties = new ValueMap();

        public SignInForm(final String id)
        {
            super(id);
            add(new PasswordTextField(PASSWORD, new PropertyModel<String>(properties, PASSWORD)));
        }

        @Override
        public final void onSubmit()
        {
            if (((AuthenticatedWebSession)getSession()).signIn("admin", getPassword())) {
                if (!continueToOriginalDestination()) {
                    setResponsePage(getApplication().getHomePage());
                }
            } else {
                error("!!!!!!!!!");
            }
        }

        private String getPassword()
        {
            return properties.getString(PASSWORD);
        }
    }
}
