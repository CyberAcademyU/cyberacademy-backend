package com.cyberacademy.security;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.auth.controller.AuthController;
import com.cyberacademy.domains.auth.dto.AuthResponse;
import com.cyberacademy.domains.auth.service.AuthService;
import com.cyberacademy.domains.auth.service.PasswordResetService;
import com.cyberacademy.domains.auth.service.RateLimitService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CookieAuthenticationTest {
    @Test void csrfRequiresBothTrustedOriginAndCustomHeader() throws Exception {
        for (String origin : new String[] {null, "null", "https://evil.example", "https://migueltalavera.site.evil.example", "https://migueltalavera.site"}) {
            for (boolean header : new boolean[] {false, true}) {
                var request = new MockHttpServletRequest("POST", "/auth/login");
                if (origin != null) request.addHeader("Origin", origin);
                if (header) request.addHeader("X-Requested-With", "CyberAcademy");
                var response = new MockHttpServletResponse();
                new OriginCsrfFilter(List.of("https://migueltalavera.site")).doFilter(request, response,
                        (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
                assertEquals(header && "https://migueltalavera.site".equals(origin) ? 204 : 403, response.getStatus());
            }
        }
    }

    @Test void cookiesHaveCorrectFlagsAndDeletionUsesSamePaths() {
        var response = new MockHttpServletResponse();
        var cookies = new SessionCookies(true);
        cookies.write(response, new SessionService.Tokens("access", "refresh", 3600));
        var headers = response.getHeaders("Set-Cookie");
        assertEquals(2, headers.size());
        for (String value : headers) {
            assertTrue(value.contains("HttpOnly")); assertTrue(value.contains("Secure"));
            assertTrue(value.contains("SameSite=Strict")); assertFalse(value.contains("Domain="));
        }
        assertTrue(headers.get(1).contains("Path=/api/v1/auth/refresh"));
        var deleted = new MockHttpServletResponse(); cookies.clear(deleted);
        assertTrue(deleted.getHeaders("Set-Cookie").stream().allMatch(value -> value.contains("Max-Age=0")));
        assertTrue(deleted.getHeaders("Set-Cookie").get(1).contains("Path=/api/v1/auth/refresh"));
    }

    @Test void loginBodyContainsUserButNeverCredentials() throws Exception {
        var auth = mock(AuthService.class); var sessions = mock(SessionService.class);
        UUID id = UUID.randomUUID();
        when(auth.login(any())).thenReturn(ApiResponse.ok("OK", new AuthResponse(
                new AuthResponse.UserSummary(id, "Test", "User", "test@example.com", "STUDENT"))));
        when(sessions.create(id)).thenReturn(new SessionService.Tokens("secret-access", "secret-refresh", 3600));
        var controller = new AuthController(auth, mock(PasswordResetService.class), sessions, new SessionCookies(true),
                mock(CurrentUserProvider.class), mock(RateLimitService.class), mock(PasswordTransportService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new OriginCsrfFilter(List.of("https://migueltalavera.site"))).build();
        var result = mvc.perform(post("/auth/login").header("Origin", "https://migueltalavera.site")
                .header("X-Requested-With", "CyberAcademy").contentType("application/json")
                .content("{\"email\":\"test@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.token").doesNotExist()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("secret-"));
        assertFalse(result.getResponse().getContentAsString().contains("test-password"));
        assertEquals(2, result.getResponse().getHeaders("Set-Cookie").size());
    }

    @Test void bearerAndUrlTokensAreNeverUsedForAuthentication() throws Exception {
        var sessions = mock(SessionService.class);
        var request = new MockHttpServletRequest("GET", "/profile");
        request.addHeader("Authorization", "Bearer secret"); request.addParameter("token", "secret");
        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(sessions).doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        verify(sessions).authenticate(null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void duplicateCookiesAreRejected() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookies.ACCESS, "first"), new Cookie(SessionCookies.ACCESS, "second"));
        assertNull(SessionCookies.read(request, SessionCookies.ACCESS));
    }
}
