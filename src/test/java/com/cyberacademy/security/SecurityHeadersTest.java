package com.cyberacademy.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Cada defensa del navegador se comprueba aquí para que no desaparezca sin avisar. */
class SecurityHeadersTest {

    private MockHttpServletResponse run(boolean secure) throws Exception {
        var request = new MockHttpServletRequest("GET", "/modules");
        request.setSecure(secure);
        var response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request, response, (req, res) -> { });
        return response;
    }

    @Test
    void includesAllBrowserBoundaryHeaders() throws Exception {
        var response = run(true);
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("0", response.getHeader("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("same-origin", response.getHeader("Cross-Origin-Opener-Policy"));
        assertEquals("same-site", response.getHeader("Cross-Origin-Resource-Policy"));
        assertEquals("none", response.getHeader("X-Permitted-Cross-Domain-Policies"));
        assertEquals("no-store", response.getHeader("Cache-Control"));

        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("form-action 'none'"));
        // Una API JSON no debe permitir scripts ni marcos propios.
        assertFalse(csp.contains("unsafe-inline"));

        String permissions = response.getHeader("Permissions-Policy");
        assertNotNull(permissions);
        assertTrue(permissions.contains("camera=()"));
        assertTrue(permissions.contains("microphone=()"));
        assertTrue(permissions.contains("geolocation=()"));
    }

    @Test
    void hstsOnlyOnSecureConnections() throws Exception {
        assertEquals("max-age=31536000; includeSubDomains", run(true).getHeader("Strict-Transport-Security"));
        assertNull(run(false).getHeader("Strict-Transport-Security"));
    }
}
