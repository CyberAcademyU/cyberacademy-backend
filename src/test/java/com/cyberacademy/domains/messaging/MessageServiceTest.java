package com.cyberacademy.domains.messaging;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    private final MessageRepository repository = mock(MessageRepository.class);
    private final CurrentUserProvider current = mock(CurrentUserProvider.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final MessageService service = new MessageService(repository, current, limits);
    private User actor(UserRole role) {
        var user = new User(); user.setId(UUID.randomUUID()); user.setRole(role);
        user.setFirstName("María"); user.setLastName("Pérez");
        when(current.requireCurrentUser()).thenReturn(user);
        return user;
    }
    private MessageService.SendRequest request(MessageService.Audience audience, List<UUID> ids) {
        return new MessageService.SendRequest(audience, ids, " Aviso para el grupo ", UUID.randomUUID());
    }

    @Test void studentCannotSendAndAdminCannotUseInbox() {
        actor(UserRole.STUDENT);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.send(request(MessageService.Audience.ALL, List.of()))).getStatusCode().value());
        actor(UserRole.ADMIN);
        assertThrows(ResponseStatusException.class, () -> service.inbox(null));
        assertThrows(ResponseStatusException.class, service::claim);
        assertThrows(ResponseStatusException.class, () -> service.read(1));
        assertThrows(ResponseStatusException.class, () -> service.delete(1));
        assertThrows(ResponseStatusException.class, service::clear);
        verifyNoInteractions(repository);
    }

    @Test void sendSnapshotsAuthenticatedSenderAndDeliversAllThroughEligibleQuery() {
        User admin = actor(UserRole.ADMIN);
        var request = request(MessageService.Audience.ALL, List.of());
        when(repository.create(eq(admin.getId()), eq("María Pérez"), eq("Aviso para el grupo"), eq(request.requestId()), anyString()))
                .thenReturn(Optional.of(7L));
        when(repository.deliver(7L, true, List.of())).thenReturn(3);
        assertEquals(new MessageService.SendResult(7, 3), service.send(request));
        verify(repository).recordCount(7L, 3);
        verify(limits).check("admin-message:" + admin.getId());
    }

    @Test void selectedRecipientsMustAllBeEligibleStudents() {
        actor(UserRole.ADMIN);
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(repository.eligibleCount(ids)).thenReturn(1L);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.send(request(MessageService.Audience.SELECTED, ids))).getStatusCode().value());
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void rejectsEmptySelectionDuplicateIdsAndBlankText() {
        actor(UserRole.ADMIN);
        UUID student = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () -> service.send(request(MessageService.Audience.SELECTED, List.of())));
        assertThrows(ResponseStatusException.class, () -> service.send(request(MessageService.Audience.SELECTED, List.of(student, student))));
        assertThrows(ResponseStatusException.class, () -> service.send(new MessageService.SendRequest(MessageService.Audience.ALL, List.of(), "   ", UUID.randomUUID())));
        verifyNoInteractions(repository);
    }

    @Test void ownershipComesFromSessionNotRequestParameters() {
        User student = actor(UserRole.STUDENT);
        when(repository.inbox(student.getId(), 99L, 21)).thenReturn(List.of());
        service.inbox(99L);
        verify(repository).inbox(student.getId(), 99L, 21);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.read(5)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.delete(5)).getStatusCode().value());
        verify(repository).markRead(student.getId(), 5);
        verify(repository).delete(student.getId(), 5);
        service.clear();
        verify(repository).clear(student.getId());
    }

    @Test void failedDeliveryDoesNotReportSuccess() {
        var admin = actor(UserRole.ADMIN);
        var request = request(MessageService.Audience.ALL, List.of());
        when(repository.create(eq(admin.getId()), anyString(), anyString(), any(), anyString())).thenReturn(Optional.of(1L));
        assertThrows(ResponseStatusException.class, () -> service.send(request));
        verify(repository, never()).recordCount(anyLong(), anyInt());
    }
}
