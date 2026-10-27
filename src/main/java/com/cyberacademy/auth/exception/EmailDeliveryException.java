package com.cyberacademy.auth.exception;

/**
 * Se lanza cuando el código OTP no pudo entregarse por correo.
 * Al ser una RuntimeException dentro de un método @Transactional,
 * provoca el rollback del registro: no queda un usuario sin poder verificar.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
