package robert_neat.his_backend.patient;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.events.PatientAdmitted;
import robert_neat.his_backend.patient.events.PatientDischarged;
import robert_neat.his_backend.staff.StaffMember;
import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.StaffRole;
import robert_neat.his_backend.staff.Ward;
import robert_neat.his_backend.staff.WardRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pacjenci i przyjecia. Reguly: `Patient.status` zmienia wylacznie ten serwis (przyjecie/wypis); co najwyzej jedno
 * aktywne przyjecie na pacjenta (409); przyjecie `outpatient` tworzy Encounter `visit`, pozostale `hospitalization`;
 * wypis zamyka Admission i powiazany Encounter. Konflikty i bledy pol sprawdzane jawnie (przed ograniczeniami bazy).
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by("lastName", "firstName"),
            "lastName", "firstName", "birthDate", "mrn", "status", "createdAt");

    /** Pola `PatientUpdateRequest`, ktore PATCH moze zmieniac (reszta - `id`, `mrn`, `status`, audyt - jest ignorowana). */
    private static final List<String> PATCHABLE = List.of("pesel", "noPeselReason", "identityDocument",
            "firstName", "secondName", "lastName", "birthDate", "gender", "phone", "email", "address",
            "emergencyContact", "insurance", "bloodType", "flags");

    private final PatientRepository patients;
    private final AdmissionRepository admissions;
    private final EncounterRepository encounters;
    private final DischargeSummaryNotes summaryNotes;
    private final WardRepository wards;
    private final StaffMemberRepository staff;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;
    private final Validator validator;
    private final JsonMapper mapper;

    @PersistenceContext
    private EntityManager em;

    PatientService(PatientRepository patients, AdmissionRepository admissions, EncounterRepository encounters,
            DischargeSummaryNotes summaryNotes, WardRepository wards, StaffMemberRepository staff,
            CurrentActor currentActor,
            ApplicationEventPublisher events, Validator validator, JsonMapper mapper) {
        this.patients = patients;
        this.admissions = admissions;
        this.encounters = encounters;
        this.summaryNotes = summaryNotes;
        this.wards = wards;
        this.staff = staff;
        this.currentActor = currentActor;
        this.events = events;
        this.validator = validator;
        this.mapper = mapper;
    }

    // --- odczyt ---

    public PageResponse<PatientSummaryResponse> list(String term, PatientStatus status, UUID wardId,
            Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<Patient> page = patients.findAll(PatientSpecifications.matching(term, status, wardId), pageable);
        List<PatientSummaryResponse> items = summaries(page.getContent());
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    public PatientResponse get(String id) {
        Patient patient = require(id);
        return PatientMapper.toResponse(patient, activeAdmission(patient.getId()).orElse(null));
    }

    /** Pacjent o danym PESEL jako podsumowanie albo pusty. */
    public Optional<PatientSummaryResponse> findByPesel(String pesel) {
        return patients.findByPesel(pesel).map(p -> summaries(List.of(p)).getFirst());
    }

    /** Podsumowania wskazanych pacjentow wg id (dla projekcji `ResultWithPatient` w innych modulach); nieznane id pomijane. */
    public Map<UUID, PatientSummaryResponse> summariesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return summaries(patients.findAllById(ids)).stream()
                .collect(Collectors.toMap(PatientSummaryResponse::id, s -> s));
    }

    public List<AdmissionResponse> admissions(String patientId, AdmissionRecordStatus status) {
        Patient patient = require(patientId);
        return admissions.history(patient.getId(), status).stream().map(PatientMapper::toResponse).toList();
    }

    // --- rejestracja i edycja ---

    @Transactional
    public PatientResponse create(PatientCreateRequest request) {
        List<FieldError> errors = crossFieldErrors(request);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (request.pesel() != null && patients.existsByPesel(request.pesel())) {
            throw new ConflictException("Pacjent o podanym numerze PESEL juz istnieje");
        }
        Patient saved = patients.saveAndFlush(Patient.register(nextMrn(), request));
        return PatientMapper.toResponse(saved, null);
    }

    /**
     * PATCH: brak pola = bez zmian, {@code null} = wyczyszczenie. Obiekty zagniezdzone (adres, ubezpieczenie...)
     * zastepowane w calosci. Zmiana jest scalana ze stanem biezacym i walidowana jak przy rejestracji.
     */
    @Transactional
    public PatientResponse update(String id, ObjectNode patch) {
        Patient patient = require(id);
        Long version = versionOf(patch);
        if (version != null && version != patient.getVersion()) {
            throw new ConflictException(
                    "Pacjent zostal zmodyfikowany przez inna osobe; odswiez dane i sprobuj ponownie");
        }
        PatientCreateRequest merged = merge(patient, patch);
        List<FieldError> errors = new ArrayList<>();
        for (ConstraintViolation<PatientCreateRequest> v : validator.validate(merged)) {
            errors.add(new FieldError(v.getPropertyPath().toString(), v.getMessage(),
                    v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
        }
        if (errors.isEmpty()) {
            errors.addAll(crossFieldErrors(merged));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (merged.pesel() != null && patients.existsByPeselAndIdNot(merged.pesel(), patient.getId())) {
            throw new ConflictException("Pacjent o podanym numerze PESEL juz istnieje");
        }
        patient.apply(merged);
        patients.saveAndFlush(patient);
        return PatientMapper.toResponse(patient, activeAdmission(patient.getId()).orElse(null));
    }

    // --- przyjecie i wypis ---

    @Transactional
    public PatientResponse admit(String patientId, AdmitPatientRequest request) {
        Patient patient = require(patientId);
        if (admissions.existsByPatientIdAndStatus(patient.getId(), AdmissionRecordStatus.ACTIVE)) {
            throw new ConflictException("Pacjent ma juz aktywne przyjecie");
        }
        List<FieldError> errors = new ArrayList<>();
        if (!wards.existsById(request.wardId())) {
            errors.add(new FieldError("wardId", "Oddzial o podanym identyfikatorze nie istnieje", "notFound"));
        }
        Optional<StaffMember> physician = staff.findById(request.attendingPhysicianId());
        if (physician.isEmpty() || physician.get().getRole() != StaffRole.DOCTOR) {
            errors.add(new FieldError("attendingPhysicianId",
                    "Lekarz prowadzacy musi byc pracownikiem w roli doctor", "notFound"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        boolean outpatient = request.admissionType() == AdmissionType.OUTPATIENT;
        Encounter encounter = encounters.saveAndFlush(Encounter.start(patient.getId(),
                outpatient ? EncounterType.VISIT : EncounterType.HOSPITALIZATION, request.admittedAt(),
                request.wardId(), request.attendingPhysicianId(), request.reason()));
        Admission admission = admissions.saveAndFlush(Admission.open(patient.getId(), encounter.getId(), request));
        patient.changeStatus(outpatient ? PatientStatus.OUTPATIENT : PatientStatus.ADMITTED);
        patients.saveAndFlush(patient);
        events.publishEvent(new PatientAdmitted(patient.getId(), admission.getId(), encounter.getId(),
                admission.getWardId(), admission.getAttendingPhysicianId(), admission.getAdmissionType(),
                admission.getAdmittedAt(), currentActor.staffId().orElse(null)));
        return PatientMapper.toResponse(patient, admission);
    }

    @Transactional
    public PatientResponse discharge(String patientId, DischargePatientRequest request) {
        Patient patient = require(patientId);
        Admission admission = admissions.findByPatientIdAndStatus(patient.getId(), AdmissionRecordStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException("Pacjent nie ma aktywnego przyjecia"));
        if (request.version() != null && request.version() != admission.getVersion()) {
            throw new ConflictException(
                    "Przyjecie zostalo zmodyfikowane przez inna osobe; odswiez dane i sprobuj ponownie");
        }
        List<FieldError> errors = new ArrayList<>();
        if (request.dischargedAt().isBefore(admission.getAdmittedAt())) {
            errors.add(new FieldError("dischargedAt", "Data wypisu nie moze byc wczesniejsza niz data przyjecia",
                    "beforeAdmission"));
        }
        if (request.summaryNoteId() != null
                && !summaryNotes.existsForPatient(request.summaryNoteId(), patient.getId())) {
            errors.add(new FieldError("summaryNoteId", "Notatka epikryzy nie istnieje dla tego pacjenta",
                    "notFound"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        admission.discharge(request.dischargedAt(), request.disposition(), request.summaryNoteId());
        if (admission.getEncounterId() != null) {
            encounters.findById(admission.getEncounterId()).ifPresent(e -> e.finish(request.dischargedAt()));
        }
        patient.changeStatus(PatientStatus.DISCHARGED);
        admissions.saveAndFlush(admission);
        patients.saveAndFlush(patient);
        events.publishEvent(new PatientDischarged(patient.getId(), admission.getId(), admission.getEncounterId(),
                admission.getWardId(), admission.getDischargedAt(), admission.getDischargeDisposition(),
                admission.getDischargeSummaryNoteId(), currentActor.staffId().orElse(null)));
        return PatientMapper.toResponse(patient, null);
    }

    // --- pomocnicze ---

    /** `id` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private Patient require(String id) {
        UUID uuid = parse(id);
        return (uuid == null ? Optional.<Patient>empty() : patients.findById(uuid))
                .orElseThrow(() -> NotFoundException.of("Pacjent", id));
    }

    private Optional<Admission> activeAdmission(UUID patientId) {
        return admissions.findByPatientIdAndStatus(patientId, AdmissionRecordStatus.ACTIVE);
    }

    /** Reguly miedzypolowe, ktorych nie wyraza Bean Validation. */
    private static List<FieldError> crossFieldErrors(PatientCreateRequest r) {
        List<FieldError> errors = new ArrayList<>();
        if (r.pesel() == null && r.noPeselReason() == null) {
            errors.add(new FieldError("noPeselReason",
                    "Powod braku numeru PESEL jest wymagany, gdy pesel jest pusty", "required"));
        }
        return errors;
    }

    private PatientCreateRequest merge(Patient patient, ObjectNode patch) {
        ObjectNode state = mapper.valueToTree(PatientMapper.toDraft(patient));
        for (String field : PATCHABLE) {
            if (patch.has(field)) {
                state.set(field, patch.get(field));
            }
        }
        try {
            return mapper.treeToValue(state, PatientCreateRequest.class);
        } catch (JacksonException e) {
            String field = e.getPath().stream().map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull).collect(Collectors.joining("."));
            throw new ValidationFailedException(List.of(new FieldError(field.isEmpty() ? "body" : field,
                    "Niepoprawna wartosc lub typ pola", "invalidFormat")));
        }
    }

    private static Long versionOf(ObjectNode patch) {
        JsonNode node = patch.get("version");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            throw new ValidationFailedException("version", "Wersja musi byc liczba calkowita");
        }
        return node.asLong();
    }

    /** Podsumowania listy: oddzial i lozko z aktywnego przyjecia (tylko `admitted`), wczytane zbiorczo. */
    private List<PatientSummaryResponse> summaries(List<Patient> content) {
        List<UUID> admittedIds = content.stream().filter(p -> p.getStatus() == PatientStatus.ADMITTED)
                .map(Patient::getId).toList();
        Map<UUID, Admission> active = new HashMap<>();
        Map<UUID, Ward> wardLookup = new HashMap<>();
        if (!admittedIds.isEmpty()) {
            admissions.findByPatientIdInAndStatus(admittedIds, AdmissionRecordStatus.ACTIVE)
                    .forEach(a -> active.put(a.getPatientId(), a));
            Set<UUID> wardIds = active.values().stream().map(Admission::getWardId).collect(Collectors.toSet());
            wards.findAllById(wardIds).forEach(w -> wardLookup.put(w.getId(), w));
        }
        return content.stream().map(p -> {
            Admission a = active.get(p.getId());
            Ward w = a == null ? null : wardLookup.get(a.getWardId());
            return PatientMapper.toSummary(p, w == null ? null : w.getName(), a == null ? null : a.getBed());
        }).toList();
    }

    private String nextMrn() {
        Number next = (Number) em.createNativeQuery("select nextval('patient_mrn_seq')").getSingleResult();
        return "HIS/%d/%06d".formatted(Year.now().getValue(), next.longValue());
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
