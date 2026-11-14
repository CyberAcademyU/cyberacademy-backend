package com.cyberacademy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookies {
    public static final String ACCESS = "ca_access";
    public static final String REFRESH = "ca_refresh";
    public static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private final boolean secure;

    public SessionCookies(@Value("${security.session.secure:true}") boolean secure) { this.secure = secure; }

    public void write(HttpServletResponse response, SessionService.Tokens tokens) {
        set(response, ACCESS, tokens.access(), "/", SessionService.ACCESS_SECONDS);
        set(response, REFRESH, tokens.refresh(), REFRESH_PATH, tokens.refreshSeconds());
    }

    public void clear(HttpServletResponse response) {
        set(response, ACCESS, "", "/", 0);
        set(response, REFRESH, "", REFRESH_PATH, 0);
    }

    private void set(HttpServletResponse response, String name, String value, String path, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true)
                .secure(secure).sameSite("Strict").path(path).maxAge(seconds).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        String value = null;
        for (var cookie : request.getCookies()) {
            if (!cookie.getName().equals(name)) continue;
            if (value != null) return null; // Rechazar cookies ambiguas.
            value = cookie.getValue();
        }
        return value;
    }
}
