package com.cyberacademy.domains.auth.dto;

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

    // Null cuando la contraseña llega cifrada en `sealedPassword`.
    @Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
    private String password;

    private SealedSecret sealedPassword;

    public VerifyOtpRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public SealedSecret getSealedPassword() { return sealedPassword; }
    public void setSealedPassword(SealedSecret sealedPassword) { this.sealedPassword = sealedPassword; }
}
