package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.ImagingExam;
import robert_neat.his_backend.catalog.ImagingExamRepository;
import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.catalog.ScheduleSlot;
import robert_neat.his_backend.catalog.ScheduleSlotRepository;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.order.OrderCancelRequest;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.OrderStatusUpdateRequest;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.ehr.Coding;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.patient.EncounterRepository;
import robert_neat.his_backend.patient.PatientRepository;

/**
 * Zlecenia badan obrazowych. Aktor zawsze z sesji (`orderedById` z zadania jest ignorowany); `patientId` w ciele
 * musi byc zgodny ze sciezka (422). Nazwa, modalnosc i okolica badania zapisywane jako snapshot z katalogu. Podanie
 * slotu rezerwuje go (`available=false`, zlecenie od razu `scheduled`); zajety slot = 409. Status zmieniaja
 * wylacznie akcje `status` i `cancel` wg {@link ImagingOrderStateMachine} (niedozwolone przejscie lub niezgodna
 * `version` = 409, `specimen_collected` = 422); kazda zmiana dopisuje wpis historii i publikuje
 * {@link ImagingOrderStatusChanged} (konsument: AlertEventListener). Anulowanie zwalnia slot. DTO mapowane w transakcji.
 */
@Service
@Transactional(readOnly = true)
public class ImagingOrderService {

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "orderedAt"),
            "orderedAt", "scheduledAt", "status", "urgency", "modality", "createdAt");

    private final ImagingOrderRepository orders;
    private final ImagingExamRepository exams;
    private final ScheduleSlotRepository slots;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    ImagingOrderService(ImagingOrderRepository orders, ImagingExamRepository exams, ScheduleSlotRepository slots,
            PatientRepository patients, EncounterRepository encounters, CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.exams = exams;
        this.slots = slots;
        this.patients = patients;
        this.encounters = encounters;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    public PageResponse<ImagingOrderResponse> list(UUID patientId, OrderStatus status, Urgency urgency,
            ImagingModality modality, Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<ImagingOrder> page = orders.findAll(
                ImagingOrderSpecifications.matching(patientId, status, urgency, modality), pageable);
        return PageResponse.from(page, ImagingOrderMapper::toResponse);
    }

    public ImagingOrderResponse get(String orderId) {
        return ImagingOrderMapper.toResponse(require(orderId));
    }

    // --- zapis ---

    /**
     * 404 pacjent; 422 walidacja (badanie, lateralnosc, kontrast, lista kontrolna, slot: brak/modalnosc, kontakt,
     * zgodnosc `patientId`); 409 slot zajety.
     */
    @Transactional
    public ImagingOrderResponse create(String patientId, ImagingOrderCreateRequest request) {
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
        ImagingExam exam = exams.findById(request.examCode().trim()).orElse(null);
        if (exam == null) {
            errors.add(new FieldError("examCode", "Badanie o kodzie '" + request.examCode().trim()
                    + "' nie istnieje w katalogu", "notFound"));
        }
        Laterality laterality = request.laterality() == null ? Laterality.NA : request.laterality();
        boolean contrast = Boolean.TRUE.equals(request.contrast());
        if (exam != null) {
            if (exam.isRequiresLaterality() && laterality == Laterality.NA) {
                errors.add(new FieldError("laterality", "Badanie '" + exam.getCode()
                        + "' wymaga okreslenia strony (left/right/bilateral)", "required"));
            }
            if (contrast && !exam.isContrastPossible()) {
                errors.add(new FieldError("contrast", "Badanie '" + exam.getCode()
                        + "' nie przewiduje kontrastu", "notAllowed"));
            }
        }
        if (!Boolean.TRUE.equals(request.safety().confirmed())) {
            errors.add(new FieldError("safety.confirmed",
                    "Lista kontrolna bezpieczenstwa musi byc potwierdzona", "required"));
        }
        ScheduleSlot slot = null;
        if (request.slotId() != null) {
            slot = slots.findByIdForUpdate(request.slotId()).orElse(null);
            if (slot == null) {
                errors.add(new FieldError("slotId", "Slot nie istnieje", "notFound"));
            } else if (exam != null && slot.getModality() != exam.getModality()) {
                errors.add(new FieldError("slotId", "Modalnosc slotu (" + slot.getModality().wire()
                        + ") nie odpowiada badaniu (" + exam.getModality().wire() + ")", "modalityMismatch"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (slot != null && (!slot.isAvailable()
                || orders.existsBySlotIdAndStatusNot(slot.getId(), OrderStatus.CANCELLED))) {
            throw new ConflictException("Slot jest juz zajety");
        }

        Instant now = Instant.now();
        Coding diagnosis = request.diagnosisCode() == null ? null
                : new Coding(request.diagnosisCode().system(), request.diagnosisCode().code().trim(),
                        request.diagnosisCode().display().trim());
        ImagingOrder order = ImagingOrder.place(id, request.encounterId(), actor, now, exam.getCode(), exam.getName(),
                exam.getModality(), exam.getBodyRegion(), laterality, contrast, request.clinicalIndication().trim(),
                blankToNull(request.clinicalQuestion()), diagnosis, request.urgency(), toEmbeddable(request.safety()),
                slot == null ? null : slot.getId(), slot == null ? null : slot.getStartAt());
        ImagingOrder saved;
        try {
            if (slot != null) {
                slot.reserve();
            }
            saved = orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // wyscig na `uq_imaging_order_slot_id` (poza blokada slotu) - ten sam skutek co zajety slot
            throw new ConflictException("Slot jest juz zajety");
        }
        return ImagingOrderMapper.toResponse(saved);
    }

    /** 403 (brak uprawnienia), 404, 409 (niedozwolone przejscie / `version`), 422 (`specimen_collected`, `cancelled`). */
    @Transactional
    public ImagingOrderResponse updateStatus(String orderId, OrderStatusUpdateRequest request) {
        OrderStatus target = request.status();
        if (target == OrderStatus.SPECIMEN_COLLECTED) {
            throw new ValidationFailedException("status",
                    "Status 'specimen_collected' nie dotyczy zlecen obrazowych");
        }
        if (target == OrderStatus.CANCELLED) {
            throw new ValidationFailedException("status",
                    "Anulowanie zlecenia wymaga powodu - uzyj akcji /cancel");
        }
        return transition(orderId, target, blankToNull(request.note()), request.version());
    }

    /** 404, 409 (zlecenie `completed`/`cancelled` lub niezgodna `version`), 422 (brak `reason`). Zwalnia slot. */
    @Transactional
    public ImagingOrderResponse cancel(String orderId, OrderCancelRequest request) {
        return transition(orderId, OrderStatus.CANCELLED, request.reason().trim(), request.version());
    }

    // --- pomocnicze ---

    private ImagingOrderResponse transition(String orderId, OrderStatus target, String note, Long expectedVersion) {
        UUID actor = actor();
        ImagingOrder order = require(orderId);
        if (expectedVersion != null && expectedVersion != order.getVersion()) {
            throw new ConflictException("Zlecenie zostalo zmodyfikowane przez inna osobe (wersja "
                    + order.getVersion() + "); odswiez dane i sprobuj ponownie");
        }
        OrderStatus previous = order.getStatus();
        if (!ImagingOrderStateMachine.canTransition(previous, target)) {
            throw new ConflictException("Niedozwolone przejscie statusu zlecenia z '" + previous.wire() + "' na '"
                    + target.wire() + "'");
        }
        Instant now = Instant.now();
        order.transitionTo(target, now, actor, note);
        ImagingOrder saved = orders.saveAndFlush(order);
        if (target == OrderStatus.CANCELLED && saved.getSlotId() != null) {
            slots.findByIdForUpdate(saved.getSlotId()).ifPresent(slot -> {
                slot.release();
                slots.saveAndFlush(slot);
            });
        }
        events.publishEvent(new ImagingOrderStatusChanged(saved.getId(), saved.getPatientId(),
                saved.getOrderedById(), previous, target, now, actor, note));
        return ImagingOrderMapper.toResponse(saved);
    }

    private static SafetyChecklist toEmbeddable(ImagingOrderCreateRequest.Safety s) {
        return new SafetyChecklist(s.pregnancy(), s.pacemakerOrImplant(), s.metalFragments(), s.contrastAllergy(),
                s.creatinine(), s.egfr(), s.claustrophobia(), s.confirmed());
    }

    private ImagingOrder require(String orderId) {
        UUID id = parse(orderId);
        return (id == null ? Optional.<ImagingOrder>empty() : orders.findById(id))
                .orElseThrow(() -> NotFoundException.of("Zlecenie obrazowe", orderId));
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
