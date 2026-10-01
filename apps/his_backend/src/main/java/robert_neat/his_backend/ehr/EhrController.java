package robert_neat.his_backend.ehr;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * EHR pacjenta (API.md, par. 3). Odczyt pelny: `ehr:read` (lekarz, pielegniarka, admin). Odczyt zawezony
 * (`ehr:read-limited`: laborant, radiolog, farmaceuta) obejmuje wylacznie diagnozy, alergie, przeciwwskazania i
 * leczenie; notatki, kontakty, epizody i `ehr-summary` wymagaja `ehr:read`.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}")
public class EhrController {

    private static final String READ = "hasAuthority('ehr:read')";
    private static final String READ_LIMITED = "hasAnyAuthority('ehr:read', 'ehr:read-limited')";

    private final EhrService service;

    EhrController(EhrService service) {
        this.service = service;
    }

    @GetMapping("/ehr-summary")
    @PreAuthorize(READ)
    public EhrSummaryResponse summary(@PathVariable String patientId) {
        return service.summary(patientId);
    }

    @GetMapping("/encounters")
    @PreAuthorize(READ)
    public List<EncounterResponse> encounters(@PathVariable String patientId) {
        return service.encounters(patientId);
    }

    @GetMapping("/episodes")
    @PreAuthorize(READ)
    public List<TreatmentEpisodeResponse> episodes(@PathVariable String patientId) {
        return service.episodes(patientId);
    }

    @GetMapping("/clinical-notes")
    @PreAuthorize(READ)
    public List<ClinicalNoteResponse> notes(@PathVariable String patientId) {
        return service.notes(patientId);
    }

    @GetMapping("/clinical-notes/{noteId}")
    @PreAuthorize(READ)
    public ClinicalNoteResponse note(@PathVariable String patientId, @PathVariable String noteId) {
        return service.note(patientId, noteId);
    }

    @GetMapping("/diagnoses/{diagnosisId}")
    @PreAuthorize(READ_LIMITED)
    public DiagnosisResponse diagnosis(@PathVariable String patientId, @PathVariable String diagnosisId) {
        return service.diagnosis(patientId, diagnosisId);
    }

    @GetMapping("/allergies/{allergyId}")
    @PreAuthorize(READ_LIMITED)
    public AllergyResponse allergy(@PathVariable String patientId, @PathVariable String allergyId) {
        return service.allergy(patientId, allergyId);
    }

    /** Wymaga ktoregos z `ehr:note:write*`; dozwolona kategoria zalezy od roli (403 gdy niedozwolona). */
    @PostMapping("/clinical-notes")
    @PreAuthorize("hasAnyAuthority('ehr:note:write', 'ehr:note:write-nursing', 'ehr:note:write-consultation')")
    public ResponseEntity<ClinicalNoteResponse> addNote(@PathVariable String patientId,
            @Valid @RequestBody ClinicalNoteCreateRequest request, Authentication authentication) {
        ClinicalNoteResponse created = service.addNote(patientId, request, authentication.getAuthorities());
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patientId + "/clinical-notes/" + created.id()))
                .body(created);
    }

    @GetMapping("/diagnoses")
    @PreAuthorize(READ_LIMITED)
    public List<DiagnosisResponse> diagnoses(@PathVariable String patientId) {
        return service.diagnoses(patientId);
    }

    @PostMapping("/diagnoses")
    @PreAuthorize("hasAuthority('ehr:diagnosis:write')")
    public ResponseEntity<DiagnosisResponse> addDiagnosis(@PathVariable String patientId,
            @Valid @RequestBody DiagnosisCreateRequest request) {
        DiagnosisResponse created = service.addDiagnosis(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patientId + "/diagnoses/" + created.id()))
                .body(created);
    }

    @GetMapping("/allergies")
    @PreAuthorize(READ_LIMITED)
    public List<AllergyResponse> allergies(@PathVariable String patientId) {
        return service.allergies(patientId);
    }

    @PostMapping("/allergies")
    @PreAuthorize("hasAuthority('ehr:allergy:write')")
    public ResponseEntity<AllergyResponse> addAllergy(@PathVariable String patientId,
            @Valid @RequestBody AllergyCreateRequest request) {
        AllergyResponse created = service.addAllergy(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patientId + "/allergies/" + created.id()))
                .body(created);
    }

    @GetMapping("/contraindications")
    @PreAuthorize(READ_LIMITED)
    public List<ContraindicationResponse> contraindications(@PathVariable String patientId) {
        return service.contraindications(patientId);
    }

    @GetMapping("/treatments")
    @PreAuthorize(READ_LIMITED)
    public List<TreatmentResponse> treatments(@PathVariable String patientId) {
        return service.treatments(patientId);
    }
}
