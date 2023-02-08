package net.ripe.rpki.ui.application;

import net.ripe.rpki.ui.admin.ErrorPage;
import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.PageExpiredException;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebRequestCycle;
import org.apache.wicket.protocol.http.WebResponse;

import java.lang.reflect.InvocationTargetException;

public class CertificationAdminWebRequestCycle extends WebRequestCycle {

    public CertificationAdminWebRequestCycle(WebApplication application, WebRequest request, WebResponse response) {
        super(application, request, response);
    }

    @Override
    public Page onRuntimeException(Page page, RuntimeException e) {
        if (e instanceof PageExpiredException) {
            return getPageExpiredErrorPage(e);
        }
        return new ErrorPage(e);
    }

    private Page getPageExpiredErrorPage(final RuntimeException exception) {
        Class<? extends Page> pageClass = getApplication().getApplicationSettings().getPageExpiredErrorPage();
        if (pageClass != null) {
            try {
                return pageClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                // do nothing
            }
        }
        return new ErrorPage(exception);

    }
}
