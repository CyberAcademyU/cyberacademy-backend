package com.cyberacademy.domains.messaging;

import com.cyberacademy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/messages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMessageController {
    private final MessageService service;
    public AdminMessageController(MessageService service) { this.service = service; }
    @PostMapping
    public ApiResponse<MessageService.SendResult> send(@Valid @RequestBody MessageService.SendRequest request) {
        return ApiResponse.ok("Mensaje enviado", service.send(request));
    }
}
