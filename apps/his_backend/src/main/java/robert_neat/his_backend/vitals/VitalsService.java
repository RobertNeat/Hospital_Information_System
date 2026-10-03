package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.VitalThreshold;
import robert_neat.his_backend.catalog.VitalThresholdRepository;
import robert_neat.his_backend.catalog.VitalType;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.Admission;
import robert_neat.his_backend.patient.AdmissionRecordStatus;
import robert_neat.his_backend.patient.AdmissionRepository;
import robert_neat.his_backend.patient.EncounterRepository;
import robert_neat.his_backend.patient.Patient;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.PatientService;
import robert_neat.his_backend.patient.PatientStatus;
import robert_neat.his_backend.patient.PatientSummaryResponse;
import robert_neat.his_backend.staff.WardRepository;
import robert_neat.his_backend.vitals.events.VitalAnomalyDetected;

/**
 * Parametry zyciowe (API.md, par. 7): odczyt zakresu i ostatniego pomiaru, zapis, przeglad oddzialu.
 * <ul>
 *   <li>zapis: aktor z sesji (`recordedById` z zadania ignorowane), `recordedAt` domyslnie "teraz" i nie z przyszlosci
 *       (tolerancja {@value #CLOCK_SKEW_SECONDS} s na rozjazd zegarow), 404 pacjent, 422: brak jakiegokolwiek pomiaru,
 *       wartosc poza `min`/`max` progu, pomiar nie-calkowity (poza temperatura: max 1 miejsce po przecinku),
 *       `painScore` poza 0-10, `deviceId` bez `source=monitor`, niezgodny `patientId`, nieznany `encounterId`;</li>
 *   <li>anomalie liczone serwerowo z `vital_threshold` przy zapisie i odczycie ({@link VitalAnomalyEvaluator});
 *       zdarzenie {@link VitalAnomalyDetected} dla kazdego zapisu z co najmniej jedna anomalia (`warning` lub
 *       `critical`) - alert {@link robert_neat.his_backend.alert.AlertType#VITAL_ANOMALY} rozgalezia sie wg
 *       najwyzszej z nich;</li>
 *   <li>przeglad oddzialu: pacjenci `admitted` z aktywnym przyjeciem (na oddzial `wardId` albo wszystkich), z ostatnim
 *       pomiarem i jego anomaliami; kolejnosc: krytyczne, ostrzezenia, bez anomalii (potem wiecej anomalii, nazwisko).</li>
 * </ul>
 * DTO mapowane w transakcji serwisu.
 */
@Service
@Transactional(readOnly = true)
public class VitalsService {

    static final int CLOCK_SKEW_SECONDS = 60;
    private static final int PAIN_MIN = 0;
    private static final int PAIN_MAX = 10;

    private final VitalSignsRepository vitals;
    private final VitalThresholdRepository thresholds;
    private final PatientRepository patients;
    private final PatientService patientService;
    private final AdmissionRepository admissions;
    private final EncounterRepository encounters;
    private final WardRepository wards;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    VitalsService(VitalSignsRepository vitals, VitalThresholdRepository thresholds, PatientRepository patients,
            PatientService patientService, AdmissionRepository admissions, EncounterRepository encounters,
            WardRepository wards, CurrentActor currentActor, ApplicationEventPublisher events) {
        this.vitals = vitals;
        this.thresholds = thresholds;
        this.patients = patients;
        this.patientService = patientService;
        this.admissions = admissions;
        this.encounters = encounters;
        this.wards = wards;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    /** 404 pacjent. Odczyty z okna `range` (brak = `all`), rosnaco po `recordedAt`. */
    public List<VitalSignsResponse> list(String patientId, VitalsRange range) {
        UUID id = requirePatient(patientId);
        Instant from = (range == null ? VitalsRange.ALL : range).from(Instant.now());
        List<VitalSigns> found = from == null ? vitals.findByPatientIdOrderByRecordedAtAscIdAsc(id)
                : vitals.findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAscIdAsc(id, from);
        return found.stream().map(VitalsMapper::toResponse).toList();
    }

    /** 404 pacjent; pusty wynik = brak odczytow (kontroler zwraca 204). */
    public Optional<VitalSignsResponse> latest(String patientId) {
        UUID id = requirePatient(patientId);
        return vitals.findFirstByPatientIdOrderByRecordedAtDescIdAsc(id).map(VitalsMapper::toResponse);
    }

    /** 404 oddzial (gdy podano `wardId`). */
    public List<WardVitalsRow> wardOverview(UUID wardId) {
        if (wardId != null && !wards.existsById(wardId)) {
            throw NotFoundException.of("Oddzial", wardId);
        }
        List<Admission> active = wardId == null ? admissions.findByStatus(AdmissionRecordStatus.ACTIVE)
                : admissions.findByWardIdAndStatus(wardId, AdmissionRecordStatus.ACTIVE);
        List<UUID> admittedIds = patients
                .findAllById(active.stream().map(Admission::getPatientId).distinct().toList()).stream()
                .filter(p -> p.getStatus() == PatientStatus.ADMITTED).map(Patient::getId).toList();
        if (admittedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PatientSummaryResponse> summaries = patientService.summariesByIds(admittedIds);
        Map<UUID, VitalSigns> latest = new HashMap<>();
        for (VitalSigns v : vitals.latestFor(admittedIds)) {
            // remis `recordedAt` rozstrzyga najmniejsze id, jak w odczycie `latest`
            latest.merge(v.getPatientId(), v, (a, b) -> a.getId().compareTo(b.getId()) <= 0 ? a : b);
        }
        Map<VitalType, VitalThreshold> table = thresholdTable();
        Instant now = Instant.now();
        return admittedIds.stream().map(pid -> {
            VitalSigns last = latest.get(pid);
            List<VitalAnomaly> anomalies = last == null ? List.of() : VitalAnomalyEvaluator.evaluate(last, table);
            Long agoMin = last == null ? null
                    : Math.max(0, Duration.between(last.getRecordedAt(), now).toMinutes());
            return new WardVitalsRow(summaries.get(pid), last == null ? null : VitalsMapper.toResponse(last),
                    anomalies, agoMin);
        }).sorted(Comparator.comparingInt(VitalsService::severityRank)
                .thenComparing(Comparator.comparingInt((WardVitalsRow r) -> r.anomalies().size()).reversed())
                .thenComparing(r -> r.patient().lastName(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.patient().firstName(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.patient().id())).toList();
    }

    // --- zapis ---

    /** 403 brak powiazania sesji z pracownikiem; 404 pacjent; 422 walidacja (patrz opis klasy). */
    @Transactional
    public VitalsRecordResponse record(String patientId, VitalSignsCreateRequest request) {
        UUID actor = currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
        UUID id = requirePatient(patientId);
        Map<VitalType, VitalThreshold> table = thresholdTable();
        Instant now = Instant.now();
        List<FieldError> errors = new ArrayList<>();

        if (request.patientId() != null && !request.patientId().equals(id)) {
            errors.add(new FieldError("patientId", "Identyfikator pacjenta w ciele jest niezgodny ze sciezka",
                    "mismatch"));
        }
        Instant recordedAt = request.recordedAt() == null ? now : request.recordedAt();
        if (recordedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
            errors.add(new FieldError("recordedAt", "Czas pomiaru nie moze byc z przyszlosci", "range"));
        }
        VitalSource source = request.source() == null ? VitalSource.MANUAL : request.source();
        String deviceId = blankToNull(request.deviceId());
        if (deviceId != null && source != VitalSource.MONITOR) {
            errors.add(new FieldError("deviceId", "Identyfikator urzadzenia dotyczy tylko odczytu z monitora",
                    "mismatch"));
        }
        if (request.encounterId() != null && !encounters.existsByIdAndPatientId(request.encounterId(), id)) {
            errors.add(new FieldError("encounterId", "Kontakt nie istnieje dla tego pacjenta", "notFound"));
        }

        Integer systolic = wholeInRange(VitalType.SYSTOLIC, request.systolic(), table, errors);
        Integer diastolic = wholeInRange(VitalType.DIASTOLIC, request.diastolic(), table, errors);
        Integer heartRate = wholeInRange(VitalType.HEART_RATE, request.heartRate(), table, errors);
        Integer spo2 = wholeInRange(VitalType.SPO2, request.spo2(), table, errors);
        Integer respiratoryRate = wholeInRange(VitalType.RESPIRATORY_RATE, request.respiratoryRate(), table, errors);
        BigDecimal temperature = temperatureInRange(request.temperature(), table, errors);
        Integer painScore = painScore(request.painScore(), errors);

        if (request.systolic() == null && request.diastolic() == null && request.heartRate() == null
                && request.spo2() == null && request.respiratoryRate() == null && request.temperature() == null
                && request.painScore() == null) {
            errors.add(new FieldError("measurements", "Wymagany co najmniej jeden pomiar", "required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        VitalSigns saved = vitals.saveAndFlush(VitalSigns.record(id, recordedAt, actor, request.context(), source,
                deviceId, request.encounterId(), systolic, diastolic, heartRate, spo2, respiratoryRate, temperature,
                painScore, blankToNull(request.notes())));

        List<VitalAnomaly> anomalies = VitalAnomalyEvaluator.evaluate(saved, table);
        if (!anomalies.isEmpty()) {
            events.publishEvent(new VitalAnomalyDetected(id, saved.getId(), anomalies, saved.getRecordedAt(), actor));
        }
        return new VitalsRecordResponse(VitalsMapper.toResponse(saved), anomalies);
    }

    // --- pomocnicze ---

    private static int severityRank(WardVitalsRow row) {
        if (row.anomalies().stream().anyMatch(a -> a.severity() == AnomalySeverity.CRITICAL)) {
            return 0;
        }
        return row.anomalies().isEmpty() ? 2 : 1;
    }

    private Map<VitalType, VitalThreshold> thresholdTable() {
        return thresholds.findAll().stream().collect(Collectors.toMap(VitalThreshold::getType, Function.identity(),
                (a, b) -> a, () -> new EnumMap<>(VitalType.class)));
    }

    /** Pomiar calkowity w granicach `min`/`max`; blad do `errors` (wynik `null`), brak pomiaru = `null` bez bledu. */
    private static Integer wholeInRange(VitalType type, BigDecimal value, Map<VitalType, VitalThreshold> table,
            List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        String field = type.wire();
        if (value.stripTrailingZeros().scale() > 0) {
            errors.add(new FieldError(field, "Wartosc musi byc liczba calkowita", "invalidFormat"));
            return null;
        }
        if (!withinBounds(type, value, table, errors)) {
            return null;
        }
        return value.intValueExact();
    }

    /** Temperatura: najwyzej 1 miejsce po przecinku (kolumna `numeric(4,1)`), granice `min`/`max`. */
    private static BigDecimal temperatureInRange(BigDecimal value, Map<VitalType, VitalThreshold> table,
            List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > 1) {
            errors.add(new FieldError(VitalType.TEMPERATURE.wire(),
                    "Temperatura moze miec najwyzej jedno miejsce po przecinku", "invalidFormat"));
            return null;
        }
        if (!withinBounds(VitalType.TEMPERATURE, value, table, errors)) {
            return null;
        }
        return value.setScale(1, java.math.RoundingMode.UNNECESSARY);
    }

    private static boolean withinBounds(VitalType type, BigDecimal value, Map<VitalType, VitalThreshold> table,
            List<FieldError> errors) {
        VitalThreshold t = table.get(type);
        if (t != null && (value.compareTo(t.getMin()) < 0 || value.compareTo(t.getMax()) > 0)) {
            errors.add(new FieldError(type.wire(), t.getLabel() + ": wartosc poza dopuszczalnym zakresem "
                    + VitalsMapper.plain(t.getMin()).toPlainString() + "-"
                    + VitalsMapper.plain(t.getMax()).toPlainString() + " " + t.getUnit(), "range"));
            return false;
        }
        return true;
    }

    /** Ocena bolu 0-10 (brak progow w `vital_threshold`). */
    private static Integer painScore(BigDecimal value, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > 0 || value.compareTo(BigDecimal.valueOf(PAIN_MIN)) < 0
                || value.compareTo(BigDecimal.valueOf(PAIN_MAX)) > 0) {
            errors.add(new FieldError("painScore", "Ocena bolu musi byc liczba calkowita z zakresu " + PAIN_MIN
                    + "-" + PAIN_MAX, "range"));
            return null;
        }
        return value.intValueExact();
    }

    /** `patientId` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private UUID requirePatient(String patientId) {
        UUID id = parse(patientId);
        if (id == null || !patients.existsById(id)) {
            throw NotFoundException.of("Pacjent", patientId);
        }
        return id;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
