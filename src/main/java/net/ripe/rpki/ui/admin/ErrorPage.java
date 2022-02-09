package net.ripe.rpki.ui.admin;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.wicket.Application;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.Model;

public class ErrorPage extends WebPage {

    public ErrorPage(RuntimeException e) {
        if (this.getApplication().getConfigurationType().equalsIgnoreCase(Application.DEVELOPMENT)) {
            addStackTrace(e);
        } else {
            add(new Label("stackTrace"));
        }
    }
    
    private void addStackTrace(RuntimeException e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        Label label = new Label("stackTrace","<h2>Development Mode</h2><br/>" + stackTrace.toString());
        label.add(new AttributeModifier("style", new Model<String>("display:block")));
        label.setEscapeModelStrings(true);
        add(label);
    }

}
