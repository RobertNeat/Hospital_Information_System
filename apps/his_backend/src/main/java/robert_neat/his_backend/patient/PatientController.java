package robert_neat.his_backend.patient;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import robert_neat.his_backend.common.api.PageResponse;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService service;

    PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patient:read')")
    public PageResponse<PatientSummaryResponse> list(@RequestParam(required = false) String term,
            @RequestParam(required = false) PatientStatus status, @RequestParam(required = false) UUID wardId,
            Pageable pageable) {
        return service.list(term, status, wardId, pageable);
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public PatientResponse get(@PathVariable String patientId) {
        return service.get(patientId);
    }

    /** 200 z kandydatem albo 204; PESEL w ciele (nie w URL). */
    @PostMapping("/duplicate-check")
    @PreAuthorize("hasAuthority('patient:read')")
    public ResponseEntity<PatientSummaryResponse> duplicateCheck(@Valid @RequestBody DuplicateCheckRequest request) {
        return service.findByPesel(request.pesel()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('patient:write')")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        PatientResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + created.id())).body(created);
    }

    /** Semantyka PATCH (brak pola = bez zmian, null = wyczysc) wymaga surowego drzewa JSON. */
    @PatchMapping("/{patientId}")
    @PreAuthorize("hasAuthority('patient:write')")
    public PatientResponse update(@PathVariable String patientId, @RequestBody ObjectNode patch) {
        return service.update(patientId, patch);
    }

    @GetMapping("/{patientId}/admissions")
    @PreAuthorize("hasAuthority('admission:read')")
    public List<AdmissionResponse> admissions(@PathVariable String patientId,
            @RequestParam(required = false) AdmissionRecordStatus status) {
        return service.admissions(patientId, status);
    }

    @PostMapping("/{patientId}/admissions")
    @PreAuthorize("hasAuthority('admission:admit')")
    public ResponseEntity<PatientResponse> admit(@PathVariable String patientId,
            @Valid @RequestBody AdmitPatientRequest request) {
        PatientResponse patient = service.admit(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patient.id() + "/admissions")).body(patient);
    }

    @PostMapping("/{patientId}/discharge")
    @PreAuthorize("hasAuthority('admission:discharge')")
    public PatientResponse discharge(@PathVariable String patientId,
            @Valid @RequestBody DischargePatientRequest request) {
        return service.discharge(patientId, request);
    }
}
