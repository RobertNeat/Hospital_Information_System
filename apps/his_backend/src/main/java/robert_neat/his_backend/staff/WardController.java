package robert_neat.his_backend.staff;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wards")
public class WardController {

    private final WardService service;

    WardController(WardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ward:read')")
    public List<WardResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ward:write')")
    public ResponseEntity<WardResponse> create(@Valid @RequestBody WardCreateRequest request) {
        WardResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/wards/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ward:write')")
    public WardResponse update(@PathVariable String id, @Valid @RequestBody WardUpdateRequest request) {
        return service.update(id, request);
    }
}
