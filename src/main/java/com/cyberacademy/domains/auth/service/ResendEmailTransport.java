package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "security.mail.transport", havingValue = "resend")
public class ResendEmailTransport implements EmailTransport {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailTransport.class);
    private final RestClient client;
    private final String apiKey;

    @Autowired
    public ResendEmailTransport(RestClient.Builder builder,
                               @Value("${security.mail.resend.api-key:}") String apiKey) {
        this(createClient(builder), apiKey);
    }

    ResendEmailTransport(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    private static RestClient createClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return builder.requestFactory(factory).build();
    }

    @Override
    public void send(MimeMessage message) throws MessagingException, IOException {
        if (apiKey.isBlank()) {
            throw new EmailDeliveryException("El servicio de correo no está configurado correctamente.");
        }
        var recipients = Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                .map(address -> ((InternetAddress) address).getAddress()).toList();
        var body = Map.of(
                "from", ((InternetAddress) message.getFrom()[0]).toUnicodeString(),
                "to", recipients,
                "subject", message.getSubject(),
                message.isMimeType("text/html") ? "html" : "text", message.getContent().toString());
        try {
            client.post().uri("https://api.resend.com/emails")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            if (e instanceof RestClientResponseException response) {
                log.error("Resend: envio_mensaje, http={}", response.getStatusCode().value());
            } else {
                log.error("Resend: envio_mensaje, fallo_de_conexion");
            }
            throw new EmailDeliveryException("No se pudo enviar el correo. Intenta nuevamente.");
        }
    }
}
