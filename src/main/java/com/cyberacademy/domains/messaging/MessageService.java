package com.cyberacademy.domains.messaging;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.CurrentUserProvider;
import com.cyberacademy.security.SessionService;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class MessageService {
    public enum Audience { ALL, SELECTED }
    public record SendRequest(@NotNull Audience audience, @Size(max=500) List<@NotNull UUID> recipientIds,
                              @NotBlank @Size(max=2000) String body, @NotNull UUID requestId) {}
    public record SendResult(long id, int recipientCount) {}
    public record Inbox(List<MessageRepository.Item> items, Long nextBefore, long unreadCount) {}
    public record Notifications(List<MessageRepository.Item> items, long unreadCount) {}
    public record Unread(long unreadCount) {}
    private final MessageRepository repository;
    private final CurrentUserProvider current;
    private final RateLimitService limits;
    public MessageService(MessageRepository repository, CurrentUserProvider current, RateLimitService limits) {
        this.repository = repository; this.current = current; this.limits = limits;
    }
    @Transactional
    public SendResult send(SendRequest request) {
        User sender = require(UserRole.ADMIN);
        if (request.audience() == null || request.requestId() == null || request.body() == null) throw bad();
        String body = request.body().strip();
        if (body.isBlank() || body.length() > 2000 || body.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) throw bad();
        var ids = request.recipientIds() == null ? List.<UUID>of() : request.recipientIds();
        if (ids.size() > 500 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) throw bad();
        if (request.audience() == Audience.ALL ? !ids.isEmpty() : ids.isEmpty()) throw bad();
        String hash = SessionService.hash(request.audience() + "|" + ids.stream().sorted().toList() + "|" + body);
        var previous = repository.previous(sender.getId(), request.requestId());
        if (previous.isPresent()) return replay(previous.get(), hash);
        limits.check("admin-message:" + sender.getId());
        if (request.audience() == Audience.SELECTED && repository.eligibleCount(ids) != ids.size()) throw bad();
        String name = sender.getFirstName() + " " + sender.getLastName();
        var created = repository.create(sender.getId(), name.strip(), body, request.requestId(), hash);
        if (created.isEmpty()) return replay(repository.previous(sender.getId(), request.requestId()).orElseThrow(MessageService::bad), hash);
        long id = created.get();
        int count = repository.deliver(id, request.audience() == Audience.ALL, ids);
        if (count == 0 || (request.audience() == Audience.SELECTED && count != ids.size())) throw bad();
        repository.recordCount(id, count);
        return new SendResult(id, count);
    }
    @Transactional(readOnly=true)
    public Inbox inbox(Long before) {
        User student = require(UserRole.STUDENT);
        if (before != null && before <= 0) throw bad();
        var rows = repository.inbox(student.getId(), before, 21);
        var page = rows.stream().limit(20).toList();
        Long next = rows.size() > 20 ? page.getLast().id() : null;
        return new Inbox(page, next, repository.unread(student.getId()));
    }
    @Transactional
    public Notifications claim() {
        User student = require(UserRole.STUDENT);
        return new Notifications(repository.claim(student.getId()), repository.unread(student.getId()));
    }
    @Transactional
    public Unread read(long id) {
        User student = require(UserRole.STUDENT);
        if (id <= 0 || repository.markRead(student.getId(), id) == 0) throw missing();
        return new Unread(repository.unread(student.getId()));
    }
    @Transactional
    public Unread delete(long id) {
        User student = require(UserRole.STUDENT);
        if (id <= 0 || repository.delete(student.getId(), id) == 0) throw missing();
        return new Unread(repository.unread(student.getId()));
    }
    @Transactional
    public Unread clear() {
        User student = require(UserRole.STUDENT);
        repository.clear(student.getId());
        return new Unread(0);
    }
    private User require(UserRole role) {
        User user = current.requireCurrentUser();
        if (user.getRole() != role) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
    private static SendResult replay(MessageRepository.Sent previous, String hash) {
        if (!previous.hash().equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "El envío ya existe con otro contenido.");
        return new SendResult(previous.id(), previous.recipientCount());
    }
    private static ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa el texto y selecciona estudiantes activos y verificados (máximo 500 en un envío específico).");
    }
    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no disponible.");
    }
}
