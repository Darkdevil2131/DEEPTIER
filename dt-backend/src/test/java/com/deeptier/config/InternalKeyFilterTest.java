package com.deeptier.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalKeyFilterTest {

    @Test
    void enforcesConstantTimeInternalKeyOnlyOnInternalRoutes() throws Exception {
        DeepTierProperties props = new DeepTierProperties(
                null, new DeepTierProperties.InternalProps("secret-key-2026"), null, null);
        InternalKeyFilter filter = new InternalKeyFilter(props);

        // Missing header -> 401
        MockHttpServletRequest missingReq = new MockHttpServletRequest("GET", "/internal/tools/get_tier_map");
        MockHttpServletResponse missingRes = new MockHttpServletResponse();
        filter.doFilter(missingReq, missingRes, new MockFilterChain());
        assertEquals(401, missingRes.getStatus());

        // Wrong header -> 401
        MockHttpServletRequest wrongReq = new MockHttpServletRequest("GET", "/internal/tools/get_tier_map");
        wrongReq.addHeader("X-Internal-Key", "wrong-key");
        MockHttpServletResponse wrongRes = new MockHttpServletResponse();
        filter.doFilter(wrongReq, wrongRes, new MockFilterChain());
        assertEquals(401, wrongRes.getStatus());

        // Valid header -> 200 pass-through
        MockHttpServletRequest okReq = new MockHttpServletRequest("GET", "/internal/tools/get_tier_map");
        okReq.addHeader("X-Internal-Key", "secret-key-2026");
        MockHttpServletResponse okRes = new MockHttpServletResponse();
        filter.doFilter(okReq, okRes, new MockFilterChain());
        assertEquals(200, okRes.getStatus());

        // Public API route -> 200 without key
        MockHttpServletRequest pubReq = new MockHttpServletRequest("GET", "/api/tier-map");
        MockHttpServletResponse pubRes = new MockHttpServletResponse();
        filter.doFilter(pubReq, pubRes, new MockFilterChain());
        assertEquals(200, pubRes.getStatus());
    }
}
