package com.cyberacademy.domains.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos editables del perfil. Los opcionales vacíos se guardan como null. */
public class UpdateProfileRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
    private String lastName;

    @Pattern(regexp = "^$|^[A-Za-z0-9_.-]{3,30}$",
            message = "El alias debe tener de 3 a 30 caracteres: letras sin tilde, números, punto, guion o guion bajo")
    private String displayName;

    @Size(max = 280, message = "La biografía admite hasta 280 caracteres")
    private String bio;

    @Pattern(regexp = "^[\\p{L} .'-]{0,60}$", message = "El país solo admite letras y espacios (máximo 60)")
    private String country;

    @Pattern(regexp = "^$|^op-0[1-8]$", message = "Avatar no válido")
    private String avatar;

    @NotNull(message = "Indica si quieres aparecer en la clasificación")
    private Boolean showInLeaderboard;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public Boolean getShowInLeaderboard() { return showInLeaderboard; }
    public void setShowInLeaderboard(Boolean showInLeaderboard) { this.showInLeaderboard = showInLeaderboard; }
}
