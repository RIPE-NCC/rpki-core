package net.ripe.rpki.rest.security;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rest.exception.RequestEntityTooLargeException;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A servlet filter that wraps the {@link ServletInputStream input stream} to throw a
 * {@link RequestEntityTooLargeException RequestEntityTooLargeException} when the request body size exceeds
 * {@link #MAX_REQUEST_SIZE} bytes.
 * <p>
 * Sets the {@link HttpServletResponse#setStatus(int) response status} to
 * {@link HttpServletResponse#SC_REQUEST_ENTITY_TOO_LARGE 413} when the {@link RequestEntityTooLargeException exception}
 * is thrown. Due to some magical reasons the {@link net.ripe.rpki.rest.exception.RestExceptionControllerAdvice#exceptionsResultingInRequestEntityTooLargeHandler(HttpServletRequest, RequestEntityTooLargeException) Spring controller advice}
 * is also needed for the Spring REST API to set the correct status.
 */
@Component
@Slf4j
public class RequestEntitySizeLimiterServletFilter implements Filter {
    public static final int MAX_REQUEST_SIZE = 1_000_000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        chain.doFilter(new RequestEntitySizeLimiterServletRequest(httpRequest), response);
    }

    private static class RequestEntitySizeLimiterServletRequest extends HttpServletRequestWrapper {
        private ServletInputStream inputStream;
        private BufferedReader reader;

        public RequestEntitySizeLimiterServletRequest(HttpServletRequest httpRequest) throws IOException {
            super(httpRequest);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader already called");
            }
            if (inputStream == null) {
                inputStream = new RequestEntitySizeLimiterInputStream(super.getInputStream(), MAX_REQUEST_SIZE);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (inputStream != null) {
                throw new IllegalStateException("getInputStream already called");
            }
            if (reader == null) {
                String characterEncoding = getCharacterEncoding();
                if (characterEncoding == null) {
                    characterEncoding = "ISO-8859-1";
                }
                reader = new BufferedReader(new InputStreamReader(
                    new RequestEntitySizeLimiterInputStream(super.getInputStream(), MAX_REQUEST_SIZE),
                    characterEncoding
                ));
            }
            return reader;
        }
    }

    private static class RequestEntitySizeLimiterInputStream extends ServletInputStream {
        private final ServletInputStream wrapped;
        private final int maximumLength;

        private int count = 0;

        public RequestEntitySizeLimiterInputStream(ServletInputStream wrapped, int maximumLength) {
            this.wrapped = wrapped;
            this.maximumLength = maximumLength;
        }

        @Override
        public int read() throws IOException {
            int result = wrapped.read();
            if (result != -1) {
                ++count;
                if (count > maximumLength) {
                    throw new RequestEntityTooLargeException();
                }
            }
            return result;
        }

        @Override
        public boolean isFinished() {
            return wrapped.isFinished();
        }

        @Override
        public boolean isReady() {
            return wrapped.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            wrapped.setReadListener(listener);
        }
    }

}
