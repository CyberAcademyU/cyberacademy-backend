package com.cyberacademy.domains.auth.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "security.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailTransport implements EmailTransport {
    private final JavaMailSender sender;

    public SmtpEmailTransport(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(MimeMessage message) {
        sender.send(message);
    }
}
