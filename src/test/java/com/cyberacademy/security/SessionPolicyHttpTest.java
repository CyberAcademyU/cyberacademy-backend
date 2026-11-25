package com.cyberacademy.security;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=SessionPolicyController.class,
        properties="security.cors.allowed-origins=https://test.example")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SessionPolicyHttpTest {
    @Autowired MockMvc mvc;
    @MockBean SessionService sessions;

    @BeforeEach void setup() {
        var user = new User(); user.setEmail("student@example.com"); user.setRole(UserRole.STUDENT);
        when(sessions.authenticate("session-cookie")).thenReturn(user);
    }

    @Test void statusRequiresAuthenticationAndDoesNotRecordActivity() throws Exception {
        mvc.perform(get("/session")).andExpect(status().isUnauthorized());
        when(sessions.status("session-cookie")).thenReturn(new SessionService.Status(UUID.randomUUID(), UUID.randomUUID(), 1000));
        mvc.perform(get("/session").cookie(new Cookie(SessionCookies.ACCESS, "session-cookie")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.idleRemainingMillis").value(1000));
        verify(sessions, never()).activity(any(), anyLong());
    }

    @Test void activityRequiresOriginHeaderAndBoundedAge() throws Exception {
        mvc.perform(post("/session/activity").cookie(new Cookie(SessionCookies.ACCESS, "session-cookie"))
                .contentType("application/json").content("{\"ageMillis\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/session/activity").cookie(new Cookie(SessionCookies.ACCESS, "session-cookie"))
                .header("Origin", "https://test.example").header("X-Requested-With", "CyberAcademy")
                .contentType("application/json").content("{\"ageMillis\":60001}")).andExpect(status().isBadRequest());
        verify(sessions, never()).activity(any(), anyLong());
    }

    @Test void activityUsesCookieIdentityAndExpiredSessionCannotBeRevived() throws Exception {
        when(sessions.activity("session-cookie", 500)).thenReturn(new SessionService.Status(UUID.randomUUID(), UUID.randomUUID(), 899500));
        mvc.perform(post("/session/activity").cookie(new Cookie(SessionCookies.ACCESS, "session-cookie"))
                .header("Origin", "https://test.example").header("X-Requested-With", "CyberAcademy")
                .contentType("application/json").content("{\"ageMillis\":500}"))
                .andExpect(status().isOk());
        verify(sessions).activity("session-cookie", 500);
        when(sessions.authenticate("session-cookie")).thenReturn(null);
        mvc.perform(post("/session/activity").cookie(new Cookie(SessionCookies.ACCESS, "session-cookie"))
                .header("Origin", "https://test.example").header("X-Requested-With", "CyberAcademy")
                .contentType("application/json").content("{\"ageMillis\":0}"))
                .andExpect(status().isUnauthorized());
        verify(sessions, never()).activity("session-cookie", 0);
    }
}
