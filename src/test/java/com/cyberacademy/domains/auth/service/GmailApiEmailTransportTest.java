package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.stream.Collectors;

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

    @Test
    void logsSafeOauthCodeWithoutResponseSecrets() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"private-secret\"}"));
        Logger logger = (Logger) LoggerFactory.getLogger(GmailApiEmailTransport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(EmailDeliveryException.class,
                    () -> transport.send(new MimeMessage(Session.getInstance(new Properties()))));
            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining());
            assertTrue(logged.contains("etapa=renovacion_token, http=400, codigo=invalid_grant"));
            assertFalse(logged.contains("private-secret"));
            server.verify();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void distinguishesSendFailureAndDoesNotLogUnknownProviderContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"private-secret\",\"message\":\"private-message\"}}"));
        Logger logger = (Logger) LoggerFactory.getLogger(GmailApiEmailTransport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            message.setText("test");
            message.saveChanges();
            assertThrows(EmailDeliveryException.class, () -> transport.send(message));
            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining());
            assertTrue(logged.contains("etapa=envio_mensaje, http=400, codigo=no_disponible"));
            assertFalse(logged.contains("private-secret"));
            assertFalse(logged.contains("private-message"));
            server.verify();
        } catch (jakarta.mail.MessagingException ex) {
            fail(ex);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
