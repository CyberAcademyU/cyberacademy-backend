package com.cyberacademy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ResendOtpRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo no válido")
    private String email;

    public ResendOtpRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}