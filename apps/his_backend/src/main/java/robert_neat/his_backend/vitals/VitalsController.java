package robert_neat.his_backend.vitals;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

/**
 * Parametry zyciowe (API.md, par. 7). Odczyt i przeglad oddzialu: `vitals:read` (lekarz, pielegniarka, admin);
 * zapis: `vitals:write` (lekarz, pielegniarka). Progi: {@code VitalThresholdController}.
 */
@RestController
@RequestMapping("/api/v1")
public class VitalsController {

    private final VitalsService service;

    VitalsController(VitalsService service) {
        this.service = service;
    }

    /** Odczyty pacjenta rosnaco po `recordedAt`; `range`: 24h / 7d / 30d / all (domyslnie all). */
    @GetMapping("/patients/{patientId}/vitals")
    @PreAuthorize("hasAuthority('vitals:read')")
    public List<VitalSignsResponse> list(@PathVariable String patientId,
            @RequestParam(required = false) VitalsRange range) {
        return service.list(patientId, range);
    }

    /** Ostatni odczyt albo 204, gdy pacjent nie ma odczytow. */
    @GetMapping("/patients/{patientId}/vitals/latest")
    @PreAuthorize("hasAuthority('vitals:read')")
    public ResponseEntity<VitalSignsResponse> latest(@PathVariable String patientId) {
        return service.latest(patientId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/patients/{patientId}/vitals")
    @PreAuthorize("hasAuthority('vitals:write')")
    public ResponseEntity<VitalsRecordResponse> record(@PathVariable String patientId,
            @Valid @RequestBody VitalSignsCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(patientId, request));
    }

    /** Pacjenci aktywnie przyjeci na oddzial `wardId` (brak = wszystkie oddzialy), najciezsi najpierw. */
    @GetMapping("/vitals/ward-overview")
    @PreAuthorize("hasAuthority('vitals:read')")
    public List<WardVitalsRow> wardOverview(@RequestParam(required = false) UUID wardId) {
        return service.wardOverview(wardId);
    }
}
