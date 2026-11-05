package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GmailApiEmailTransportTest {
    @Test
    void exchangesRefreshTokenAndSendsEncodedMessage() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer access"))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    String raw = new ObjectMapper().readTree(body).get("raw").asText();
                    String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                    assertTrue(mime.contains("student@example.com"));
                    assertTrue(mime.contains("123456"));
                })
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "student@example.com");
        message.setText("Código: 123456", "UTF-8");
        message.saveChanges();
        transport.send(message);
        server.verify();
    }

    @Test
    void rejectsFailedAuthorizationWithoutSending() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withBadRequest().body("invalid_grant"));
        assertThrows(EmailDeliveryException.class,
                () -> transport.send(new MimeMessage(Session.getInstance(new Properties()))));
        server.verify();
    }
}
