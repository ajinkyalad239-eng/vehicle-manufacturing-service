package com.example.manufacturing.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(MockHttpServletRequest request, MockHttpServletResponse response, AtomicReference<String> seen)
            throws Exception {
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void reusesSafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        AtomicReference<String> seen = new AtomicReference<>();

        String echoed = run(request, new MockHttpServletResponse(), seen);

        assertThat(echoed).isEqualTo("abc-123");
        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String echoed = run(new MockHttpServletRequest(), new MockHttpServletResponse(), seen);

        assertThat(echoed).hasSize(36).isEqualTo(seen.get());
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad\nvalue");
        AtomicReference<String> seen = new AtomicReference<>();

        String echoed = run(request, new MockHttpServletResponse(), seen);

        assertThat(echoed).isNotEqualTo("bad\nvalue").hasSize(36);
    }
}
