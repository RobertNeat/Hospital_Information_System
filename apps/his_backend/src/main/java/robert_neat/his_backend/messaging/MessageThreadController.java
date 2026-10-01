package robert_neat.his_backend.messaging;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import robert_neat.his_backend.common.api.PageResponse;

/** Watki i wiadomosci (API.md, par. 8). Uprawnienia wg par. 12: `message:read` / `message:write`. */
@RestController
@RequestMapping("/api/v1")
public class MessageThreadController {

    private final MessageThreadService service;

    MessageThreadController(MessageThreadService service) {
        this.service = service;
    }

    /** Watki zalogowanego uczestnika (z jego `unreadCount`), sort domyslny `lastMessageAt,desc`. */
    @GetMapping("/message-threads")
    @PreAuthorize("hasAuthority('message:read')")
    public PageResponse<MessageThreadResponse> list(@RequestParam(required = false) UUID patientId,
            Pageable pageable) {
        return service.list(patientId, pageable);
    }

    @GetMapping("/message-threads/{threadId}")
    @PreAuthorize("hasAuthority('message:read')")
    public MessageThreadResponse get(@PathVariable String threadId) {
        return service.get(threadId);
    }

    @GetMapping("/message-threads/{threadId}/messages")
    @PreAuthorize("hasAuthority('message:read')")
    public List<MessageResponse> messages(@PathVariable String threadId) {
        return service.messages(threadId);
    }

    @PostMapping("/message-threads")
    @PreAuthorize("hasAuthority('message:write')")
    public ResponseEntity<MessageThreadResponse> create(@Valid @RequestBody ThreadCreateRequest request) {
        MessageThreadResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/message-threads/" + created.id())).body(created);
    }

    @PostMapping("/message-threads/{threadId}/messages")
    @PreAuthorize("hasAuthority('message:write')")
    public ResponseEntity<MessageResponse> send(@PathVariable String threadId,
            @Valid @RequestBody MessageSendRequest request) {
        return ResponseEntity.status(201).body(service.send(threadId, request));
    }

    /** Cialo puste (kursor odczytu zalogowanego); dodatkowe ciala sa ignorowane. */
    @PostMapping("/message-threads/{threadId}/read")
    @PreAuthorize("hasAuthority('message:write')")
    public MessageThreadResponse markRead(@PathVariable String threadId) {
        return service.markRead(threadId);
    }
}
