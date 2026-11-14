package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailTransportTest {
    @Test
    void sendsBothOtpFlowsWithDecodedHtmlAndSubject() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        for (String subject : new String[]{"CyberAcademy - Tu código de verificación",
                "CyberAcademy - Recuperación de contraseña"}) {
            server.expect(requestTo("https://api.resend.com/emails"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer test-key"))
                    .andExpect(jsonPath("$.from").value("noreply@example.com"))
                    .andExpect(jsonPath("$.to[0]").value("student@example.com"))
                    .andExpect(jsonPath("$.subject").value(subject))
                    .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("123456")))
                    .andRespond(withSuccess("{\"id\":\"test-id\"}", MediaType.APPLICATION_JSON));
        }
        var email = new EmailService(new ResendEmailTransport(builder.build(), "test-key"), "noreply@example.com");
        email.sendVerificationCode("student@example.com", "Estudiante", "123456");
        email.sendPasswordResetCode("student@example.com", "123456");
        server.verify();
    }

    @Test
    void rejectsProviderFailureAndMissingKey() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withForbiddenRequest());
        var message = new MimeMessage(Session.getInstance(new Properties()));
        var helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom("noreply@example.com");
        helper.setTo("student@example.com");
        helper.setSubject("OTP");
        helper.setText("123456", true);
        assertThrows(EmailDeliveryException.class,
                () -> new ResendEmailTransport(builder.build(), "test-key").send(message));
        assertThrows(EmailDeliveryException.class,
                () -> new ResendEmailTransport(builder.build(), " ").send(message));
        server.verify();
    }
}
