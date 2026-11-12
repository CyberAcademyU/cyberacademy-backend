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
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;

class GmailApiEmailTransportTest {
    @Test
    void reusesTokenAndRefreshesBeforeExpiry() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-11-12T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh", clock);
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                        requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer first"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"second\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer second"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setText("test");
        message.saveChanges();
        transport.send(message);
        when(clock.instant()).thenReturn(start.plusSeconds(3539));
        transport.send(message);
        when(clock.instant()).thenReturn(start.plusSeconds(3540));
        transport.send(message);
        server.verify();
    }

    @Test
    void rejectedTokenIsInvalidatedWithoutRetryingTheEmail() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(builder.build(), "client", "secret", "refresh");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"rejected\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"new\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer new"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setText("test");
        message.saveChanges();
        assertThrows(EmailDeliveryException.class, () -> transport.send(message));
        transport.send(message);
        server.verify();
    }

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
