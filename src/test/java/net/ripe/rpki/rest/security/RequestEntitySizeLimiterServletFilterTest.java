package net.ripe.rpki.rest.security;

import net.ripe.rpki.rest.exception.RequestEntityTooLargeException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class RequestEntitySizeLimiterServletFilterTest {
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private RequestEntitySizeLimiterServletFilter subject;

    @Before
    public void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        subject = new RequestEntitySizeLimiterServletFilter();
    }

    @Test
    public void should_pass_data_from_wrapped_ServletInputStream() throws Exception {
        byte[] input = {1, 2, 3};
        mockRequest.setContent(input);
        subject.doFilter(mockRequest, mockResponse, (request, response) -> {
            byte[] buffer = new byte[4];
            assertThat(request.getInputStream().read(buffer)).isEqualTo(3);
            assertThat(Arrays.copyOf(buffer, 3)).isEqualTo(input);
        });
    }

    @Test
    public void should_set_status_when_input_stream_is_too_large() throws Exception {
        byte[] input = new byte[RequestEntitySizeLimiterServletFilter.MAX_REQUEST_SIZE + 1];
        mockRequest.setContent(input);

        Throwable throwable = catchThrowable(() -> subject.doFilter(mockRequest, mockResponse, (request, response) -> {
            byte[] buffer = new byte[input.length];
            request.getInputStream().read(buffer);
        }));

        assertThat(throwable).isInstanceOf(RequestEntityTooLargeException.class);
    }

    @Test
    public void should_throw_if_getReader_is_called_after_getInputStream() throws Exception {
        subject.doFilter(mockRequest, mockResponse, (request, response) -> {
            assertThat(request.getInputStream()).isNotNull();
            assertThat(request.getInputStream()).isSameAs(request.getInputStream());
            assertThatThrownBy(() -> request.getReader())
                .isInstanceOf(IllegalStateException.class)
                .extracting(x -> x.getMessage()).isEqualTo("getInputStream already called");
        });
    }

    @Test
    public void should_throw_if_getInputStream_is_called_after_getReader() throws Exception {
        subject.doFilter(mockRequest, mockResponse, (request, response) -> {
            assertThat(request.getReader()).isNotNull();
            assertThat(request.getReader()).isSameAs(request.getReader());
            assertThatThrownBy(() -> request.getInputStream())
                .isInstanceOf(IllegalStateException.class)
                .extracting(x -> x.getMessage()).isEqualTo("getReader already called");
        });
    }
}
