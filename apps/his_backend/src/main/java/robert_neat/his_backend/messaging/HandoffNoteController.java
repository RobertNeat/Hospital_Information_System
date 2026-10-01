package robert_neat.his_backend.messaging;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Przekazanie zmiany (API.md, par. 8). Uprawnienia wg par. 12 (jak zadania): `task:read` / `task:write`. */
@RestController
@RequestMapping("/api/v1")
public class HandoffNoteController {

    private final HandoffNoteService service;

    HandoffNoteController(HandoffNoteService service) {
        this.service = service;
    }

    /** Filtr `wardId` (kontrakt) oraz dodatkowy `shiftDate` (ISO-8601, data). */
    @GetMapping("/handoff-notes")
    @PreAuthorize("hasAuthority('task:read')")
    public List<HandoffNoteResponse> list(@RequestParam(required = false) UUID wardId,
            @RequestParam(required = false) LocalDate shiftDate) {
        return service.list(wardId, shiftDate);
    }

    @PostMapping("/handoff-notes")
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<HandoffNoteResponse> create(@Valid @RequestBody HandoffNoteCreateRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }
}
