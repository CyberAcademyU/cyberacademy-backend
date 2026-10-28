package com.cyberacademy.common.exception;

/**
 * Conflicto con el estado actual del recurso (HTTP 409), por ejemplo un código duplicado.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
