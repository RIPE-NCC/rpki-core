package net.ripe.rpki.ui.application;

import net.ripe.rpki.server.api.services.command.CertificateAuthorityConcurrentModificationException;
import net.ripe.rpki.ui.admin.ErrorPage;
import org.apache.wicket.Page;
import org.apache.wicket.WicketRuntimeException;
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
        Throwable cause = unwrapCause(e);
        if (cause instanceof CertificateAuthorityConcurrentModificationException) {
            return new CertificateAuthorityConcurrentModificationPage((CertificateAuthorityConcurrentModificationException) cause, page);
        }

        if (e instanceof PageExpiredException) {
            return getPageExpiredErrorPage(e);
        }
        return new ErrorPage(e);
    }

    private Throwable unwrapCause(RuntimeException e) {
        Throwable cause = e;
        if (cause instanceof WicketRuntimeException) {
            cause = cause.getCause();
        }
        if (cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        return cause;
    }

    private Page getPageExpiredErrorPage(final RuntimeException exception) {
        Class<? extends Page> pageClass = getApplication().getApplicationSettings().getPageExpiredErrorPage();
        if (pageClass != null) {
            try {
                return pageClass.newInstance();
            } catch (InstantiationException e) {
                // do nothing
            } catch (IllegalAccessException e) {
                // do nothing
            }
        }
        return new ErrorPage(exception);

    }
}
