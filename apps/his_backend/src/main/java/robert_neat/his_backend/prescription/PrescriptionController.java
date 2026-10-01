package robert_neat.his_backend.prescription;

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

/** Recepty (API.md, par. 6) i kontrola bezpieczenstwa leku. Uprawnienia wg par. 12: `prescription:*`, `drug-safety-check:run`. */
@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {

    private final PrescriptionService prescriptions;
    private final DrugSafetyService safety;

    PrescriptionController(PrescriptionService prescriptions, DrugSafetyService safety) {
        this.prescriptions = prescriptions;
        this.safety = safety;
    }

    /** Lista globalna i per pacjent (`patientId`); dodatkowe filtry `status` (efektywny) i `kind`. */
    @GetMapping("/prescriptions")
    @PreAuthorize("hasAuthority('prescription:read')")
    public PageResponse<PrescriptionResponse> list(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID prescriberId, @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(required = false) PrescriptionKind kind, Pageable pageable) {
        return prescriptions.list(patientId, prescriberId, status, kind, pageable);
    }

    @GetMapping("/prescriptions/{prescriptionId}")
    @PreAuthorize("hasAuthority('prescription:read')")
    public PrescriptionResponse get(@PathVariable String prescriptionId) {
        return prescriptions.get(prescriptionId);
    }

    @GetMapping("/patients/{patientId}/active-medications")
    @PreAuthorize("hasAuthority('prescription:read')")
    public List<ActiveMedicationResponse> activeMedications(@PathVariable String patientId) {
        return prescriptions.activeMedications(patientId);
    }

    @PostMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('prescription:create')")
    public ResponseEntity<PrescriptionResponse> issue(@PathVariable String patientId,
            @Valid @RequestBody PrescriptionCreateRequest request) {
        PrescriptionResponse created = prescriptions.issue(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/prescriptions/" + created.id())).body(created);
    }

    /** Cialo opcjonalne (`reason?`, `version?`). */
    @PostMapping("/prescriptions/{prescriptionId}/cancel")
    @PreAuthorize("hasAuthority('prescription:cancel')")
    public PrescriptionResponse cancel(@PathVariable String prescriptionId,
            @RequestBody(required = false) PrescriptionCancelRequest request) {
        return prescriptions.cancel(prescriptionId, request);
    }

    /** Odczyt wyliczany (POST tylko ze wzgledu na cialo): 200, nie 201. */
    @PostMapping("/drug-safety-checks")
    @PreAuthorize("hasAuthority('drug-safety-check:run')")
    public List<DrugSafetyWarningResponse> checkSafety(@Valid @RequestBody DrugSafetyCheckRequest request) {
        return safety.check(request);
    }
}
