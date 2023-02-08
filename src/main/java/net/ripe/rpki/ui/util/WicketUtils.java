package net.ripe.rpki.ui.util;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.image.ContextImage;


public final class WicketUtils {

    private WicketUtils() {
        //Utility classes should not have a public or default constructor.
    }

    public static ContextImage getStatusImage(String id, boolean status) {
        String imagePath = status ? "tick.gif" : "cross.gif";
        return new ContextImage(id, "static/images/" + imagePath);
    }

    public static PageParameters caIdToPageParameters(long id) {
        PageParameters params = new PageParameters();
        params.add("caId", String.valueOf(id));
        return params;
    }
}
