package com.dogdog.nomat.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void replacesUnsafeRequestIdAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/maps");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                requestIdDuringRequest.set(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY))
        );

        assertThat(requestIdDuringRequest.get()).isNotBlank().isNotEqualTo("unsafe request id");
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .isEqualTo(requestIdDuringRequest.get());
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }
}
