package com.cyberacademy.domains.messaging;

import com.cyberacademy.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
@PreAuthorize("hasRole('STUDENT')")
public class StudentMessageController {
    private final MessageService service;
    public StudentMessageController(MessageService service) { this.service = service; }
    @GetMapping
    public ApiResponse<MessageService.Inbox> inbox(@RequestParam(required=false) Long before) {
        return ApiResponse.ok("Bandeja obtenida", service.inbox(before));
    }
    @PostMapping("/notifications/claim")
    public ApiResponse<MessageService.Notifications> claim() {
        return ApiResponse.ok("Notificaciones", service.claim());
    }
    @PutMapping("/{id}/read")
    public ApiResponse<MessageService.Unread> read(@PathVariable long id) {
        return ApiResponse.ok("Mensaje leído", service.read(id));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<MessageService.Unread> delete(@PathVariable long id) {
        return ApiResponse.ok("Mensaje eliminado de tu bandeja", service.delete(id));
    }
    @DeleteMapping
    public ApiResponse<MessageService.Unread> clear() {
        return ApiResponse.ok("Bandeja vaciada", service.clear());
    }
}
