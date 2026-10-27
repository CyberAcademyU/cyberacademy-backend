package com.cyberacademy.auth.dto;

import java.util.UUID;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresInSeconds;
    private UserSummary user;

    public AuthResponse(String token, long expiresInSeconds, UserSummary user) {
        this.token = token;
        this.expiresInSeconds = expiresInSeconds;
        this.user = user;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public UserSummary getUser() { return user; }

    public static class UserSummary {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String role;

        public UserSummary(UUID id, String firstName, String lastName, String email, String role) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.role = role;
        }

        public UUID getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
    }
}