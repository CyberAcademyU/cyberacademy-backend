package com.cyberacademy.domains.messaging;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.security.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={AdminMessageController.class, StudentMessageController.class},
        properties="security.cors.allowed-origins=https://test.example")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MessageHttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean MessageService messages;
    @MockBean SessionService sessions;
    private final String payload = """
        {"audience":"ALL","recipientIds":[],"body":"Aviso","requestId":"00000000-0000-0000-0000-000000000001"}
        """;
    @BeforeEach void setup() {
        var admin = new User(); admin.setEmail("admin@example.com"); admin.setRole(UserRole.ADMIN);
        var student = new User(); student.setEmail("student@example.com"); student.setRole(UserRole.STUDENT);
        when(sessions.authenticate("admin-cookie")).thenReturn(admin);
        when(sessions.authenticate("student-cookie")).thenReturn(student);
        when(messages.inbox(null)).thenReturn(new MessageService.Inbox(List.of(), null, 0));
    }
    @Test void anonymousCannotReadMessagesOrSendThem() throws Exception {
        mvc.perform(get("/messages")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/messages").header("Origin", "https://test.example")
                .header("X-Requested-With", "CyberAcademy").contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(messages);
    }
    @Test void studentsCannotSendAndAdministratorsCannotReceive() throws Exception {
        mvc.perform(post("/admin/messages").cookie(new Cookie(SessionCookies.ACCESS, "student-cookie"))
                .header("Origin", "https://test.example").header("X-Requested-With", "CyberAcademy")
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(get("/messages").cookie(new Cookie(SessionCookies.ACCESS, "admin-cookie")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(messages);
    }
    @Test void studentCanReadOwnInboxButWritesRequireOriginAndCustomHeader() throws Exception {
        mvc.perform(get("/messages").cookie(new Cookie(SessionCookies.ACCESS, "student-cookie")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(delete("/messages/1").cookie(new Cookie(SessionCookies.ACCESS, "student-cookie"))
                .header("Origin", "https://evil.example").header("X-Requested-With", "CyberAcademy"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/messages").cookie(new Cookie(SessionCookies.ACCESS, "student-cookie"))
                .header("Origin", "https://test.example")).andExpect(status().isForbidden());
        verify(messages, never()).delete(anyLong());
        verify(messages, never()).clear();
    }
}
