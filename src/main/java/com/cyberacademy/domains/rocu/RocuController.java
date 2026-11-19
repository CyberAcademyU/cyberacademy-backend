package com.cyberacademy.domains.rocu;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.auth.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/rocu")
public class RocuController {
    public record Turn(@NotBlank @Size(max = 1200) String question,
                       @NotBlank @Size(max = 4000) String answer) {}
    public record Chat(@NotBlank @Size(max = 1200) String message,
                       @NotNull @Size(max = 4) List<@NotNull @Valid Turn> history) {}

    private final GeminiService gemini;
    private final RateLimitService limits;

    public RocuController(GeminiService gemini, RateLimitService limits) {
        this.gemini = gemini;
        this.limits = limits;
    }

    @PostMapping("/chat")
    public ApiResponse<String> chat(@Valid @RequestBody Chat chat, HttpServletRequest request) {
        limits.checkChat(request.getRemoteAddr());
        return ApiResponse.ok("Respuesta de Rocu", gemini.reply(chat));
    }
}
