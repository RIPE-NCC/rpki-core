package net.ripe.rpki.ui.util;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.injection.web.InjectorHolder;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.model.Model;


public final class WicketUtils {

    private WicketUtils() {
        //Utility classes should not have a public or default constructor.
    }

    public static ContextImage getStatusImage(String id, boolean status) {
        String imagePath = status ? "tick.gif" : "cross.gif";
        return new ContextImage(id, "static/images/" + imagePath);
    }

    public static ContextImage getUnknownStatusImage(String id) {
        String imagePath = "warning.gif";
        return new ContextImage(id, "static/images/" + imagePath);
    }

    /**
        * Inject beans into injectionTarget for classes not managed by Spring
        */
    public static void springInjection(Object injectionTarget) {
        InjectorHolder.getInjector().inject(injectionTarget);
    }

    private static String pushGAEventCode(String s1, String s2) {
        return String.format("if (window['_gaq']) { _gaq.push(['_trackEvent', 'RPKI', '%s', '%s',, false]); }", s1, s2);
    }

    public static Component addGAEvent(Component component, String s1, String s2) {
        return component.add(new AttributeAppender("onclick", new Model<String>(pushGAEventCode(s1,s2)), ";"));
    }

    public static PageParameters caIdToPageParameters(long id) {
        PageParameters params = new PageParameters();
        params.add("caId", String.valueOf(id));
        return params;
    }
}
