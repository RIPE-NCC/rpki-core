package net.ripe.rpki.rest.security;

import net.ripe.rpki.server.api.security.RunAsUserHolder;
import net.ripe.rpki.rest.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SpringAuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_REQ_ATTR = "userId";
    public static final String REQUEST_ID_CONTEXT = "requestId";

    private final Logger log = LoggerFactory.getLogger(SpringAuthInterceptor.class);

    public SpringAuthInterceptor() {
        log.info("Initializing " + getClass().getSimpleName());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.debug("Running before call auth logic");
        final String requestId = request.getHeader(RestService.REQUEST_ID_HEADER);
        if (requestId != null) {
            MDC.put(REQUEST_ID_CONTEXT, requestId);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("Running after call auth logic");
        Object userId = request.getAttribute(USER_ID_REQ_ATTR);
        if (userId != null) {
            RunAsUserHolder.clear();
            request.removeAttribute(USER_ID_REQ_ATTR);
        }
        MDC.remove(REQUEST_ID_CONTEXT);
    }

}
