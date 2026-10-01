package robert_neat.his_backend.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.Drug;
import robert_neat.his_backend.catalog.DrugRepository;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.EncounterRepository;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;
import robert_neat.his_backend.prescription.events.PrescriptionIssued;

/**
 * Recepty. Aktor zawsze z sesji (`prescriberId` z zadania jest ignorowany); `patientId` w ciele musi byc zgodny ze
 * sciezka (422). Nazwa, substancja, moc i postac leku sa kopiowane z katalogu (snapshot); `accessCode` i `eRxKey`
 * generuje serwer ({@link PrescriptionCodeGenerator}). Ostrzezenia bezpieczenstwa sa doradcze - wystawienie ich nie
 * blokuje. Anulowanie recepty "dispensed"/"cancelled"/"expired" (takze wygaslej wg terminu) lub przy niezgodnej
 * `version` to 409. Zdarzenia domenowe publikowane w transakcji, brak konsumenta. DTO mapowane w transakcji.
 */
@Service
@Transactional(readOnly = true)
public class PrescriptionService {

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "issuedAt"),
            "issuedAt", "validFrom", "validUntil", "status", "kind", "createdAt");

    private final PrescriptionRepository prescriptions;
    private final DrugRepository drugs;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final PrescriptionCodeGenerator codes;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    PrescriptionService(PrescriptionRepository prescriptions, DrugRepository drugs, PatientRepository patients,
            EncounterRepository encounters, PrescriptionCodeGenerator codes, CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.prescriptions = prescriptions;
        this.drugs = drugs;
        this.patients = patients;
        this.encounters = encounters;
        this.codes = codes;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    public PageResponse<PrescriptionResponse> list(UUID patientId, UUID prescriberId, PrescriptionStatus status,
            PrescriptionKind kind, Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        LocalDate today = Prescription.today();
        Page<Prescription> page = prescriptions.findAll(
                PrescriptionSpecifications.matching(patientId, prescriberId, status, kind, today), pageable);
        return PageResponse.from(page, p -> PrescriptionMapper.toResponse(p, today));
    }

    public PrescriptionResponse get(String prescriptionId) {
        return PrescriptionMapper.toResponse(require(prescriptionId), Prescription.today());
    }

    /** Pozycje zywych recept pacjenta (404, gdy brak pacjenta). */
    public List<ActiveMedicationResponse> activeMedications(String patientId) {
        return activeMedications(requirePatient(patientId));
    }

    /** Pozycje recept `issued`/`partially_dispensed` z `validUntil` >= dzis, wg poczatku waznosci malejaco. */
    List<ActiveMedicationResponse> activeMedications(UUID patientId) {
        List<ActiveMedicationResponse> result = new ArrayList<>();
        for (Prescription p : prescriptions.findActive(patientId,
                EnumSet.of(PrescriptionStatus.ISSUED, PrescriptionStatus.PARTIALLY_DISPENSED),
                Prescription.today())) {
            p.getItems().forEach(i -> result.add(PrescriptionMapper.toActiveMedication(p, i)));
        }
        return result;
    }

    // --- zapis ---

    /** 404 pacjent; 422 walidacja (daty, pozycje, istniejacy lek, droga i refundacja dozwolone dla leku, kontakt). */
    @Transactional
    public PrescriptionResponse issue(String patientId, PrescriptionCreateRequest request) {
        UUID actor = actor();
        UUID id = requirePatient(patientId);
        List<FieldError> errors = new ArrayList<>();
        if (request.patientId() != null && !request.patientId().equals(id)) {
            errors.add(new FieldError("patientId", "Identyfikator pacjenta w ciele jest niezgodny ze sciezka",
                    "mismatch"));
        }
        if (request.encounterId() != null && !encounters.existsByIdAndPatientId(request.encounterId(), id)) {
            errors.add(new FieldError("encounterId", "Kontakt nie istnieje dla tego pacjenta", "notFound"));
        }
        if (request.validUntil().isBefore(request.validFrom())) {
            errors.add(new FieldError("validUntil", "Koniec waznosci nie moze byc przed jej poczatkiem",
                    "beforeValidFrom"));
        }
        List<PrescriptionItem> items = buildItems(request, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Instant now = Instant.now();
        Prescription saved = prescriptions.saveAndFlush(Prescription.issue(id, request.encounterId(), actor, now,
                request.validFrom(), request.validUntil(), request.kind(), codes.accessCode(), codes.eRxKey(),
                blankToNull(request.notes()), items));
        events.publishEvent(new PrescriptionIssued(saved.getId(), id, actor, saved.getKind(), saved.getValidFrom(),
                saved.getValidUntil(), saved.getItems().size(), now));
        return PrescriptionMapper.toResponse(saved, Prescription.today());
    }

    /** 404; 409 (`dispensed`/`cancelled`/`expired` albo niezgodna `version`). `request` moze byc `null` (brak ciala). */
    @Transactional
    public PrescriptionResponse cancel(String prescriptionId, PrescriptionCancelRequest request) {
        UUID actor = actor();
        Prescription prescription = require(prescriptionId);
        if (request != null && request.version() != null && request.version() != prescription.getVersion()) {
            throw new ConflictException("Recepta zostala zmodyfikowana przez inna osobe (wersja "
                    + prescription.getVersion() + "); odswiez dane i sprobuj ponownie");
        }
        LocalDate today = Prescription.today();
        PrescriptionStatus current = prescription.effectiveStatus(today);
        if (!current.isOpen()) {
            throw new ConflictException("Nie mozna anulowac recepty w statusie '" + current.wire() + "'");
        }
        Instant now = Instant.now();
        String reason = request == null ? null : blankToNull(request.reason());
        prescription.cancel(now, reason);
        Prescription saved = prescriptions.saveAndFlush(prescription);
        events.publishEvent(new PrescriptionCancelled(saved.getId(), saved.getPatientId(), saved.getPrescriberId(),
                actor, reason, now));
        return PrescriptionMapper.toResponse(saved, today);
    }

    // --- pomocnicze ---

    /** Pozycje ze snapshotem z katalogu; bledy pol (`items[i].drugId`, `.dosage.route`, `.reimbursement`) do listy. */
    private List<PrescriptionItem> buildItems(PrescriptionCreateRequest request, List<FieldError> errors) {
        List<PrescriptionItemRequest> requested = request.items();
        Set<UUID> ids = new HashSet<>();
        requested.forEach(i -> ids.add(i.drugId()));
        Map<UUID, Drug> catalog = drugs.findAllById(ids).stream()
                .collect(Collectors.toMap(Drug::getId, Function.identity()));

        List<PrescriptionItem> items = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            PrescriptionItemRequest item = requested.get(i);
            Drug drug = catalog.get(item.drugId());
            if (drug == null) {
                errors.add(new FieldError("items[" + i + "].drugId",
                        "Lek o id '" + item.drugId() + "' nie istnieje w katalogu", "notFound"));
                continue;
            }
            DosageRequest d = item.dosage();
            boolean valid = true;
            if (!drug.getRoutes().contains(d.route())) {
                errors.add(new FieldError("items[" + i + "].dosage.route", "Droga podania '" + d.route().wire()
                        + "' nie jest dozwolona dla leku '" + drug.getName() + "'", "notAllowed"));
                valid = false;
            }
            if (!drug.getReimbursementOptions().contains(item.reimbursement())) {
                errors.add(new FieldError("items[" + i + "].reimbursement", "Poziom refundacji '"
                        + item.reimbursement().wire() + "' nie jest dostepny dla leku '" + drug.getName() + "'",
                        "notAllowed"));
                valid = false;
            }
            if (valid) {
                DosageInstruction dosage = DosageInstruction.of(d.dose(), d.doseUnit().trim(), d.route(),
                        d.frequency(), d.durationDays(), d.asNeeded(), d.maxPerDay(), blankToNull(d.instructions()));
                Set<TimeOfDay> times = d.timesOfDay() == null || d.timesOfDay().isEmpty()
                        ? EnumSet.noneOf(TimeOfDay.class) : EnumSet.copyOf(d.timesOfDay());
                items.add(PrescriptionItem.of(drug, dosage, times, item.quantityPackages(), item.reimbursement(),
                        item.substitutionAllowed()));
            }
        }
        return items;
    }

    private Prescription require(String prescriptionId) {
        UUID id = parse(prescriptionId);
        return (id == null ? java.util.Optional.<Prescription>empty() : prescriptions.findById(id))
                .orElseThrow(() -> NotFoundException.of("Recepta", prescriptionId));
    }

    /** `patientId` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private UUID requirePatient(String patientId) {
        UUID id = parse(patientId);
        if (id == null || !patients.existsById(id)) {
            throw NotFoundException.of("Pacjent", patientId);
        }
        return id;
    }

    private UUID actor() {
        return currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
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
