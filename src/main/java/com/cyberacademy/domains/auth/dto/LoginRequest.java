package com.cyberacademy.domains.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo no válido")
    private String email;

    // El formulario nuevo envía `sealedPassword`; este campo solo se usa para
    // clientes antiguos mientras PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION=false.
    @Size(max = 128, message = "La contraseña no es correcta")
    private String password;

    private SealedSecret sealedPassword;

    public LoginRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public SealedSecret getSealedPassword() { return sealedPassword; }
    public void setSealedPassword(SealedSecret sealedPassword) { this.sealedPassword = sealedPassword; }
}
