package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "security.mail.transport", havingValue = "gmail-api")
public class GmailApiEmailTransport implements EmailTransport {
    private static final Logger log = LoggerFactory.getLogger(GmailApiEmailTransport.class);
    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    @Autowired
    public GmailApiEmailTransport(RestClient.Builder builder,
            @Value("${security.mail.gmail.client-id:}") String clientId,
            @Value("${security.mail.gmail.client-secret:}") String clientSecret,
            @Value("${security.mail.gmail.refresh-token:}") String refreshToken) {
        this(createClient(builder), clientId, clientSecret, refreshToken);
    }

    GmailApiEmailTransport(RestClient client, String clientId, String clientSecret, String refreshToken) {
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    private static RestClient createClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return builder.requestFactory(factory).build();
    }

    @Override
    public void send(MimeMessage message) throws MessagingException, IOException {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw new EmailDeliveryException("El servicio de correo no está configurado correctamente.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            TokenResponse token = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (token == null || token.access_token() == null || token.access_token().isBlank()) {
                throw new EmailDeliveryException("No se pudo autorizar el servicio de correo.");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            message.writeTo(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            client.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(headers -> headers.setBearerAuth(token.access_token()))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // Nunca registrar respuestas OAuth: pueden contener información sensible.
            log.error("Falló la autorización o el envío mediante Gmail API ({}).", e.getClass().getSimpleName());
            throw new EmailDeliveryException("No se pudo enviar el correo. Intenta nuevamente.");
        }
    }

    private record TokenResponse(String access_token) {}
}
