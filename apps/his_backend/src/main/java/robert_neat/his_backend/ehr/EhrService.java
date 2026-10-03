package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.ehr.events.AllergyRecorded;
import robert_neat.his_backend.ehr.events.ClinicalNoteCreated;
import robert_neat.his_backend.ehr.events.DiagnosisRecorded;
import robert_neat.his_backend.patient.EncounterRepository;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.TreatmentEpisodeRepository;

/**
 * EHR pacjenta: odczyty list, `ehr-summary` (projekcja backendu) oraz zapis notatek, diagnoz i alergii. Aktor zawsze
 * z sesji (pola `authorId`/`diagnosedById`/`recordedById` nie wystepuja w zadaniach zapisu); `patientId` w ciele musi byc
 * zgodny ze sciezka (422). Zdarzenia domenowe publikowane w transakcji; konsument: `alert/AlertEventListener`.
 * DTO mapowane w transakcji (open-in-view wylaczone).
 */
@Service
@Transactional(readOnly = true)
public class EhrService {

    private static final int RECENT_LIMIT = 5;

    /** Kategorie notatek wg uprawnien zapisu (API.md, par. 12): lekarz - wszystkie, pielegniarka, radiolog - zawezone. */
    private static final Set<NoteCategory> NURSE_CATEGORIES = EnumSet.of(NoteCategory.NURSING, NoteCategory.OBSERVATION);
    private static final Set<NoteCategory> RADIOLOGIST_CATEGORIES = EnumSet.of(NoteCategory.CONSULTATION);

    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final TreatmentEpisodeRepository episodes;
    private final ClinicalNoteRepository notes;
    private final DiagnosisRepository diagnoses;
    private final AllergyRepository allergies;
    private final ContraindicationRepository contraindications;
    private final TreatmentRepository treatments;
    private final List<ActiveMedicationsProvider> medicationProviders;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    EhrService(PatientRepository patients, EncounterRepository encounters, TreatmentEpisodeRepository episodes,
            ClinicalNoteRepository notes, DiagnosisRepository diagnoses, AllergyRepository allergies,
            ContraindicationRepository contraindications, TreatmentRepository treatments,
            List<ActiveMedicationsProvider> medicationProviders, CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.patients = patients;
        this.encounters = encounters;
        this.episodes = episodes;
        this.notes = notes;
        this.diagnoses = diagnoses;
        this.allergies = allergies;
        this.contraindications = contraindications;
        this.treatments = treatments;
        this.medicationProviders = medicationProviders;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    public EhrSummaryResponse summary(String patientId) {
        UUID id = requirePatient(patientId);
        List<PrescriptionItemView> medications = new ArrayList<>();
        medicationProviders.forEach(p -> medications.addAll(p.activeMedications(id)));
        return new EhrSummaryResponse(
                diagnoses.findTop5ByPatientIdOrderByDiagnosedAtDescIdAsc(id).stream().map(EhrMapper::toResponse)
                        .toList(),
                diagnoses.findByPatientIdAndTypeOrderByDiagnosedAtDescIdAsc(id, DiagnosisType.CHRONIC).stream()
                        .map(EhrMapper::toResponse).toList(),
                medications,
                encounters.findTop5ByPatientIdOrderByStartAtDescIdAsc(id).stream().map(EhrMapper::toResponse)
                        .toList(),
                allergies.findByPatientIdOrderByRecordedAtDescIdAsc(id).stream().map(EhrMapper::toResponse)
                        .toList());
    }

    public List<EncounterResponse> encounters(String patientId) {
        UUID id = requirePatient(patientId);
        return encounters.findByPatientIdOrderByStartAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    public List<TreatmentEpisodeResponse> episodes(String patientId) {
        UUID id = requirePatient(patientId);
        return episodes.findByPatientIdOrderByStartAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    public List<ClinicalNoteResponse> notes(String patientId) {
        UUID id = requirePatient(patientId);
        return notes.findByPatientIdOrderByCreatedAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    public ClinicalNoteResponse note(String patientId, String noteId) {
        UUID id = requirePatient(patientId);
        return notes.findByIdAndPatientId(requireId("Notatka kliniczna", noteId), id).map(EhrMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Notatka kliniczna", noteId));
    }

    public DiagnosisResponse diagnosis(String patientId, String diagnosisId) {
        UUID id = requirePatient(patientId);
        return diagnoses.findByIdAndPatientId(requireId("Diagnoza", diagnosisId), id).map(EhrMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Diagnoza", diagnosisId));
    }

    public AllergyResponse allergy(String patientId, String allergyId) {
        UUID id = requirePatient(patientId);
        return allergies.findByIdAndPatientId(requireId("Alergia", allergyId), id).map(EhrMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Alergia", allergyId));
    }

    public List<DiagnosisResponse> diagnoses(String patientId) {
        UUID id = requirePatient(patientId);
        return diagnoses.findByPatientIdOrderByDiagnosedAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    public List<AllergyResponse> allergies(String patientId) {
        UUID id = requirePatient(patientId);
        return allergies.findByPatientIdOrderByRecordedAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    public List<ContraindicationResponse> contraindications(String patientId) {
        UUID id = requirePatient(patientId);
        return contraindications.findByPatientIdOrderByRecordedAtDescIdAsc(id).stream().map(EhrMapper::toResponse)
                .toList();
    }

    public List<TreatmentResponse> treatments(String patientId) {
        UUID id = requirePatient(patientId);
        return treatments.findByPatientIdOrderByStartAtDescIdAsc(id).stream().map(EhrMapper::toResponse).toList();
    }

    // --- zapis ---

    /** 403, gdy rola nie moze zapisac danej kategorii; 404 pacjent; 422 walidacja (m.in. `encounterId` obcego pacjenta). */
    @Transactional
    public ClinicalNoteResponse addNote(String patientId, ClinicalNoteCreateRequest request,
            Collection<? extends GrantedAuthority> authorities) {
        if (!allowedCategories(authorities).contains(request.category())) {
            throw new ForbiddenException("Rola nie moze zapisywac notatek w kategorii '"
                    + request.category().wire() + "'");
        }
        UUID id = requirePatient(patientId);
        UUID actor = actor();
        List<FieldError> errors = new ArrayList<>();
        checkPatientMatches(id, request.patientId(), errors);
        checkEncounter(id, request.encounterId(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Set<String> symptoms = normalized(request.symptoms());
        ClinicalNote saved = notes.saveAndFlush(ClinicalNote.create(id, request.encounterId(), actor,
                request.category(), request.title().trim(), request.content().trim(), symptoms));
        events.publishEvent(new ClinicalNoteCreated(saved.getId(), id, saved.getEncounterId(), actor,
                saved.getCategory(), saved.getCreatedAt()));
        return EhrMapper.toResponse(saved);
    }

    @Transactional
    public DiagnosisResponse addDiagnosis(String patientId, DiagnosisCreateRequest request) {
        UUID id = requirePatient(patientId);
        UUID actor = actor();
        List<FieldError> errors = new ArrayList<>();
        checkPatientMatches(id, request.patientId(), errors);
        checkEncounter(id, request.encounterId(), errors);
        CodingValidation.requireSnomedIfPresent("code", request.code(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Coding code = new Coding(request.code().system(), request.code().code().trim(),
                request.code().display().trim());
        Diagnosis saved = diagnoses.saveAndFlush(Diagnosis.record(id, request.encounterId(), code, request.type(),
                request.status() == null ? DiagnosisStatus.ACTIVE : request.status(),
                request.diagnosedAt() == null ? Instant.now() : request.diagnosedAt(), actor,
                blankToNull(request.notes())));
        events.publishEvent(new DiagnosisRecorded(saved.getId(), id, saved.getEncounterId(), code, saved.getType(),
                saved.getDiagnosedAt(), actor));
        return EhrMapper.toResponse(saved);
    }

    @Transactional
    public AllergyResponse addAllergy(String patientId, AllergyCreateRequest request) {
        UUID id = requirePatient(patientId);
        UUID actor = actor();
        List<FieldError> errors = new ArrayList<>();
        checkPatientMatches(id, request.patientId(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Allergy saved = allergies.saveAndFlush(Allergy.record(id, request.substance().trim(), request.category(),
                request.reaction().trim(), request.severity(),
                request.status() == null ? AllergyStatus.ACTIVE : request.status(),
                request.recordedAt() == null ? Instant.now() : request.recordedAt(), actor,
                normalized(request.atcCodes())));
        events.publishEvent(new AllergyRecorded(saved.getId(), id, saved.getSubstance(), saved.getSeverity(),
                Set.copyOf(saved.getAtcCodes()), saved.getRecordedAt(), actor));
        return EhrMapper.toResponse(saved);
    }

    // --- pomocnicze ---

    /** Kategorie notatek dozwolone dla uprawnien zapisu `ehr:note:write*` (suma). */
    static Set<NoteCategory> allowedCategories(Collection<? extends GrantedAuthority> authorities) {
        Set<NoteCategory> allowed = EnumSet.noneOf(NoteCategory.class);
        for (GrantedAuthority authority : authorities) {
            switch (authority.getAuthority()) {
                case "ehr:note:write" -> allowed.addAll(EnumSet.allOf(NoteCategory.class));
                case "ehr:note:write-nursing" -> allowed.addAll(NURSE_CATEGORIES);
                case "ehr:note:write-consultation" -> allowed.addAll(RADIOLOGIST_CATEGORIES);
                default -> {
                }
            }
        }
        return allowed;
    }

    /** `patientId` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private UUID requirePatient(String patientId) {
        UUID id = parse(patientId);
        if (id == null || !patients.existsById(id)) {
            throw NotFoundException.of("Pacjent", patientId);
        }
        return id;
    }

    /** Identyfikator zasobu jest nieprzezroczysty: zly format to 404. */
    private static UUID requireId(String resource, String id) {
        UUID uuid = parse(id);
        if (uuid == null) {
            throw NotFoundException.of(resource, id);
        }
        return uuid;
    }

    private UUID actor() {
        return currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
    }

    /** Sciezka jest autorytatywna: `patientId` w ciele (jesli podany) musi byc zgodny (422). */
    private static void checkPatientMatches(UUID pathId, UUID bodyId, List<FieldError> errors) {
        if (bodyId != null && !bodyId.equals(pathId)) {
            errors.add(new FieldError("patientId", "Identyfikator pacjenta w ciele jest niezgodny ze sciezka",
                    "mismatch"));
        }
    }

    private void checkEncounter(UUID patientId, UUID encounterId, List<FieldError> errors) {
        if (encounterId != null && !encounters.existsByIdAndPatientId(encounterId, patientId)) {
            errors.add(new FieldError("encounterId", "Kontakt nie istnieje dla tego pacjenta", "notFound"));
        }
    }

    /** Przyciete, bez pustych i duplikatow (klucz glowny tabel to para (id, wartosc)); kolejnosc wejscia. */
    private static Set<String> normalized(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream().map(String::trim).filter(v -> !v.isEmpty()).forEach(result::add);
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
