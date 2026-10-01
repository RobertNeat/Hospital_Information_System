package robert_neat.his_backend.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alerty kliniczne (API.md, par. 8). Uprawnienia wg par. 12: `alert:read` / `alert:acknowledge`. */
@RestController
@RequestMapping("/api/v1")
public class AlertController {

    private final AlertService service;

    AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('alert:read')")
    public List<AlertResponse> list(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Boolean acknowledged) {
        return service.list(patientId, acknowledged);
    }

    /** Puste cialo (uzytkownik z sesji); idempotentne. */
    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('alert:acknowledge')")
    public AlertResponse acknowledge(@PathVariable String alertId) {
        return service.acknowledge(alertId);
    }
}
