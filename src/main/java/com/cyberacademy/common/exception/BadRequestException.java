package com.cyberacademy.common.exception;

/**
 * Petición inválida que no cubre la validación de DTOs (HTTP 400).
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
