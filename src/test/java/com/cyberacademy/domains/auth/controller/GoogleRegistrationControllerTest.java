package com.cyberacademy.domains.auth.controller;

import com.cyberacademy.domains.auth.service.*;
import com.cyberacademy.security.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleRegistrationControllerTest {
    @Test void incompleteRegistrationDoesNotCreateSessionOrClearChallenge() {
        var google = mock(GoogleIdentityService.class);
        var cookies = mock(SessionCookies.class);
        var sessions = mock(SessionService.class);
        var controller = new GoogleLoginController(google, cookies, sessions,
                mock(CurrentUserProvider.class), mock(RateLimitService.class));
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/google");
        request.setCookies(new Cookie("ca_google_nonce", "nonce"));
        var response = new MockHttpServletResponse();
        var token = Jwt.withTokenValue("credential").header("alg", "RS256").subject("google-subject").build();
        when(google.verify("credential", "nonce")).thenReturn(token);
        when(google.resolve(token, null, null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED));
        assertEquals(428, assertThrows(ResponseStatusException.class,
                () -> controller.login(new GoogleLoginController.Credential("credential", null, null), request, response))
                .getStatusCode().value());
        verifyNoInteractions(sessions, cookies);
    }
}
