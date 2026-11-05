package com.cyberacademy.domains.auth.service;

import com.cyberacademy.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailTransport transport;
    private final String from;

    public EmailService(EmailTransport transport,
                        @Value("${security.mail.from:no-reply@cyberacademy.local}") String from) {
        this.transport = transport;
        this.from = from;
    }

    public void sendPasswordResetCode(String to, String code) {
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("CyberAcademy - Recuperación de contraseña");
            helper.setText(buildCodeEmail(null, code,
                    "Usa este código para recuperar tu contraseña:",
                    "Si no solicitaste recuperar tu contraseña, puedes ignorar este mensaje."), true);
            message.saveChanges();
            transport.send(message);
        } catch (MailException | MessagingException | IOException e) {
            throw new EmailDeliveryException("No se pudo enviar el correo de recuperación.", e);
        }
    }

    /**
     * Envía el código de verificación al correo del estudiante.
     * Lanza EmailDeliveryException si el SMTP falla, para que la transacción haga rollback.
     */
    public void sendVerificationCode(String to, String firstName, String code) {
        String html = buildVerificationEmail(firstName, code);

        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("CyberAcademy - Tu código de verificación");
            helper.setText(html, true);

            message.saveChanges();
            transport.send(message);
            log.info("Código de verificación enviado a [{}]", to);

        } catch (MailException | MessagingException | IOException e) {
            log.error("No se pudo enviar el correo de verificación.");
            throw new EmailDeliveryException(
                    "No se pudo enviar el código de verificación a tu correo. Intenta nuevamente.", e);
        }
    }

    private String buildVerificationEmail(String firstName, String code) {
        return buildCodeEmail(firstName, code,
                "Gracias por registrarte. Usa este código para verificar tu correo electrónico:",
                "Si no creaste esta cuenta, puedes ignorar este mensaje.");
    }

    private String buildCodeEmail(String firstName, String code, String instruction, String disclaimer) {
        String name = (firstName == null || firstName.isBlank()) ? "estudiante" : firstName;
        return """
                <!DOCTYPE html>
                <html lang="es">
                  <body style="margin:0;padding:0;background-color:#0f172a;font-family:Arial,Helvetica,sans-serif;">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#0f172a;padding:32px 0;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;">
                            <tr>
                              <td style="background-color:#1e293b;padding:24px;text-align:center;">
                                <h1 style="margin:0;color:#22d3ee;font-size:22px;letter-spacing:1px;">CyberAcademy</h1>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:32px 32px 8px 32px;color:#1e293b;">
                                <p style="margin:0 0 12px 0;font-size:16px;">Hola <strong>{{NAME}}</strong>,</p>
                                <p style="margin:0 0 24px 0;font-size:15px;line-height:22px;color:#475569;">
                                  {{INSTRUCTION}}
                                </p>
                                <div style="text-align:center;margin:0 0 24px 0;">
                                  <span style="display:inline-block;padding:16px 28px;background-color:#0f172a;color:#22d3ee;font-size:32px;letter-spacing:8px;border-radius:8px;font-weight:bold;">{{CODE}}</span>
                                </div>
                                <p style="margin:0 0 8px 0;font-size:14px;color:#475569;">
                                  El código expira en <strong>15 minutos</strong>.
                                </p>
                                <p style="margin:0 0 24px 0;font-size:14px;color:#475569;">
                                  {{DISCLAIMER}}
                                </p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:16px 32px 24px 32px;border-top:1px solid #e2e8f0;">
                                <p style="margin:0;font-size:12px;color:#94a3b8;">
                                  Este es un correo automático, por favor no respondas a este mensaje.
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """
                .replace("{{NAME}}", HtmlUtils.htmlEscape(name))
                .replace("{{INSTRUCTION}}", instruction)
                .replace("{{DISCLAIMER}}", disclaimer)
                .replace("{{CODE}}", code);
    }
}
