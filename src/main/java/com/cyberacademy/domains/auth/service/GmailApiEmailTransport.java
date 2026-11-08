package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "security.mail.transport", havingValue = "gmail-api")
public class GmailApiEmailTransport implements EmailTransport {
    private static final Logger log = LoggerFactory.getLogger(GmailApiEmailTransport.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> SAFE_CODES = Set.of(
            "invalid_grant", "invalid_client", "invalid_request", "unauthorized_client",
            "unsupported_grant_type", "access_denied", "invalid_scope",
            "INVALID_ARGUMENT", "FAILED_PRECONDITION", "PERMISSION_DENIED", "UNAUTHENTICATED",
            "RESOURCE_EXHAUSTED", "NOT_FOUND", "INTERNAL", "UNAVAILABLE");
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
            log.error("Configuración Gmail incompleta: GMAIL_CLIENT_ID={}, GMAIL_CLIENT_SECRET={}, GMAIL_REFRESH_TOKEN={}",
                    clientId.isBlank() ? "ausente" : "presente",
                    clientSecret.isBlank() ? "ausente" : "presente",
                    refreshToken.isBlank() ? "ausente" : "presente");
            throw new EmailDeliveryException("El servicio de correo no está configurado correctamente.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        TokenResponse token;
        try {
            token = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
        } catch (RestClientException e) {
            throw deliveryFailure("renovacion_token", e);
        }
        if (token == null || token.access_token() == null || token.access_token().isBlank()) {
            log.error("Gmail API: etapa=renovacion_token, respuesta_sin_token");
            throw new EmailDeliveryException("No se pudo autorizar el servicio de correo.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        message.writeTo(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        try {
            client.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(headers -> headers.setBearerAuth(token.access_token()))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw deliveryFailure("envio_mensaje", e);
        }
    }

    private EmailDeliveryException deliveryFailure(String stage, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            log.error("Gmail API: etapa={}, http={}, codigo={}", stage,
                    response.getStatusCode().value(), safeErrorCode(response.getResponseBodyAsString()));
        } else {
            log.error("Gmail API: etapa={}, fallo_de_conexion", stage);
        }
        return new EmailDeliveryException("No se pudo enviar el correo. Intenta nuevamente.");
    }

    private String safeErrorCode(String body) {
        try {
            JsonNode error = JSON.readTree(body).path("error");
            String code = error.isTextual() ? error.asText() : error.path("status").asText();
            return SAFE_CODES.contains(code) ? code : "no_disponible";
        } catch (IOException | RuntimeException e) {
            return "no_disponible";
        }
    }

    private record TokenResponse(String access_token) {}
}
