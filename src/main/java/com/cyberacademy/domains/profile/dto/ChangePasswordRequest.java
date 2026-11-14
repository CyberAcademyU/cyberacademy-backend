package com.cyberacademy.domains.profile.dto;

import com.cyberacademy.domains.auth.dto.SealedSecret;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    // Null cuando la contraseña llega cifrada en `sealedCurrentPassword`.
    @Size(max = 128, message = "La contraseña actual no es correcta")
    private String currentPassword;

    // Null cuando la contraseña llega cifrada en `sealedNewPassword`.
    @Size(min = 8, max = 128, message = "La nueva contraseña debe tener entre 8 y 128 caracteres")
    private String newPassword;

    private SealedSecret sealedCurrentPassword;
    private SealedSecret sealedNewPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public SealedSecret getSealedCurrentPassword() { return sealedCurrentPassword; }
    public void setSealedCurrentPassword(SealedSecret sealedCurrentPassword) { this.sealedCurrentPassword = sealedCurrentPassword; }

    public SealedSecret getSealedNewPassword() { return sealedNewPassword; }
    public void setSealedNewPassword(SealedSecret sealedNewPassword) { this.sealedNewPassword = sealedNewPassword; }
}
