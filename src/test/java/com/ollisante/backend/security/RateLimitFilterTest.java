package com.ollisante.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static MockHttpServletRequest login(String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", forwardedFor);
        return req;
    }

    @Test
    void blocksAfterLimitPerIpOnly() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 3, 100);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(login("41.82.1.1"), ok, new MockFilterChain());
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(login("41.82.1.1"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("TROP_DE_DEMANDES");

        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(login("41.82.2.2"), other, new MockFilterChain());
        assertThat(other.getStatus()).isEqualTo(200);
    }

    @Test
    void usesLastForwardedAddressBecauseApacheAppendsIt() {
        assertThat(RateLimitFilter.clientIp(login("1.2.3.4, 41.82.1.1"))).isEqualTo("41.82.1.1");
        MockHttpServletRequest direct = new MockHttpServletRequest("GET", "/api/v1/countries");
        direct.setRemoteAddr("41.82.9.9");
        direct.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(RateLimitFilter.clientIp(direct)).isEqualTo("41.82.9.9");
    }

    @Test
    void neverLimitsPaymentCallbacks() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1);
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments/pawapay/callback");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
