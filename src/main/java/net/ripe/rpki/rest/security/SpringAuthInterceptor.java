package net.ripe.rpki.rest.security;

import net.ripe.rpki.server.api.security.RunAsUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class SpringAuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_REQ_ATTR = "userId";

    private final Logger log = LoggerFactory.getLogger(SpringAuthInterceptor.class);

    public SpringAuthInterceptor() {
        log.info("Initializing " + getClass().getSimpleName());
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("Running after call auth logic");
        Object userId = request.getAttribute(USER_ID_REQ_ATTR);
        if (userId != null) {
            RunAsUserHolder.clear();
            request.removeAttribute(USER_ID_REQ_ATTR);
        }
    }

}
