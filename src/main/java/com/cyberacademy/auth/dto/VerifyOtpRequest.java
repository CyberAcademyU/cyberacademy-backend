package com.cyberacademy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyOtpRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo no válido")
    private String email;

    @NotBlank(message = "El código es obligatorio")
    @Size(min = 6, max = 6, message = "El codigo de verificacion debe tener 6 dígitos")
    private String otpCode;

    public VerifyOtpRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
}