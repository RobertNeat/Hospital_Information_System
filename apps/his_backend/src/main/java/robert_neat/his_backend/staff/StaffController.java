package robert_neat.his_backend.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final StaffService service;

    StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('staff:read')")
    public List<StaffMemberResponse> list(@RequestParam(required = false) StaffRole role,
            @RequestParam(required = false) UUID wardId) {
        return service.list(role, wardId);
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasAuthority('staff:read')")
    public StaffMemberResponse get(@PathVariable String staffId) {
        return service.get(staffId);
    }

    /** Aktywacja konta (takze odblokowanie) - tylko administrator. */
    @PostMapping("/{staffId}/activate")
    @PreAuthorize("hasAuthority('account:manage')")
    public StaffMemberResponse activate(@PathVariable String staffId) {
        return service.activate(staffId);
    }

    /** Blokada konta przez administratora (nie mozna zablokowac wlasnego konta). */
    @PostMapping("/{staffId}/lock")
    @PreAuthorize("hasAuthority('account:manage')")
    public StaffMemberResponse lock(@PathVariable String staffId) {
        return service.lock(staffId);
    }
}
