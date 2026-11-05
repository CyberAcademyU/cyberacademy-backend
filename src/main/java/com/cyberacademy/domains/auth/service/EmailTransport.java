package com.cyberacademy.domains.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;

public interface EmailTransport {
    void send(MimeMessage message) throws MessagingException, IOException;
}
