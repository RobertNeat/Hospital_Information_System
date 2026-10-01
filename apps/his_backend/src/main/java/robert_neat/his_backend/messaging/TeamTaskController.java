package robert_neat.his_backend.messaging;

import java.util.List;
import java.util.UUID;

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

/** Zadania zespolu (API.md, par. 8). Uprawnienia wg par. 12: `task:read` / `task:write`. */
@RestController
@RequestMapping("/api/v1")
public class TeamTaskController {

    private final TeamTaskService service;

    TeamTaskController(TeamTaskService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('task:read')")
    public List<TeamTaskResponse> list(@RequestParam(required = false) UUID assignedToId,
            @RequestParam(required = false) UUID createdById, @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID patientId) {
        return service.list(assignedToId, createdById, status, patientId);
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<TeamTaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('task:write')")
    public TeamTaskResponse updateStatus(@PathVariable String taskId,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        return service.updateStatus(taskId, request);
    }
}
