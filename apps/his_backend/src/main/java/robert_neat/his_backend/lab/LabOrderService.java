package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.LabTest;
import robert_neat.his_backend.catalog.LabTestRepository;
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
import robert_neat.his_backend.ehr.CodingValidation;
import robert_neat.his_backend.lab.events.LabOrderPlaced;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.patient.EncounterRepository;
import robert_neat.his_backend.patient.PatientRepository;

/**
 * Zlecenia laboratoryjne. Aktor zawsze z sesji (pole `orderedById` nie wystepuje w zadaniu); `patientId` w ciele musi
 * byc zgodny ze sciezka (422). Nazwa badania i material zapisywane jako snapshot z katalogu. Status zmieniaja
 * wylacznie akcje `status` i `cancel` wg {@link LabOrderStateMachine} (niedozwolone przejscie lub niezgodna `version`
 * = 409); kazda zmiana dopisuje wpis historii i publikuje {@link LabOrderStatusChanged} (konsument: AlertEventListener); utworzenie publikuje {@link LabOrderPlaced} (konsument: integracja e-laboratory). Pielegniarka
 * (`lab-order:collect-specimen`) moze ustawic wylacznie `specimen_collected`. DTO mapowane w transakcji.
 */
@Service
@Transactional(readOnly = true)
public class LabOrderService {

    private static final String UPDATE_STATUS = "lab-order:update-status";
    private static final String COLLECT_SPECIMEN = "lab-order:collect-specimen";

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "orderedAt"),
            "orderedAt", "plannedCollectionAt", "status", "urgency", "createdAt");

    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    LabOrderService(LabOrderRepository orders, LabTestRepository tests, PatientRepository patients,
            EncounterRepository encounters, CurrentActor currentActor, ApplicationEventPublisher events) {
        this.orders = orders;
        this.tests = tests;
        this.patients = patients;
        this.encounters = encounters;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    public PageResponse<LabOrderResponse> list(UUID patientId, OrderStatus status, Urgency urgency,
            Instant orderedFrom, Instant orderedTo, Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<LabOrder> page = orders.findAll(
                LabOrderSpecifications.matching(patientId, status, urgency, orderedFrom, orderedTo), pageable);
        return PageResponse.from(page, LabOrderMapper::toResponse);
    }

    public LabOrderResponse get(String orderId) {
        return LabOrderMapper.toResponse(require(orderId));
    }

    // --- zapis ---

    /** 404 pacjent; 422 walidacja (pozycje, katalog, material, na czczo, kontakt, zgodnosc `patientId`). */
    @Transactional
    public LabOrderResponse create(String patientId, LabOrderCreateRequest request) {
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
        List<LabOrderItem> items = buildItems(request, errors);
        CodingValidation.requireSnomedIfPresent("diagnosisCode", request.diagnosisCode(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Instant now = Instant.now();
        Coding diagnosis = request.diagnosisCode() == null ? null
                : new Coding(request.diagnosisCode().system(), request.diagnosisCode().code().trim(),
                        request.diagnosisCode().display().trim());
        LabOrder saved = orders.saveAndFlush(LabOrder.place(id, request.encounterId(), actor, now, request.urgency(),
                request.fasting(), request.plannedCollectionAt(), diagnosis, request.clinicalInfo().trim(),
                blankToNull(request.notes()), items));
        events.publishEvent(new LabOrderPlaced(saved.getId(), saved.getPatientId(), actor, now));
        return LabOrderMapper.toResponse(saved);
    }

    /** 403 (rola vs docelowy status), 404, 409 (niedozwolone przejscie / `version`), 422 (`cancelled` tylko przez /cancel). */
    @Transactional
    public LabOrderResponse updateStatus(String orderId, OrderStatusUpdateRequest request,
            Collection<? extends GrantedAuthority> authorities) {
        OrderStatus target = request.status();
        requireMayChangeTo(target, authorities);
        if (target == OrderStatus.CANCELLED) {
            throw new ValidationFailedException("status",
                    "Anulowanie zlecenia wymaga powodu - uzyj akcji /cancel");
        }
        return transition(orderId, target, blankToNull(request.note()), request.version());
    }

    /** 404, 409 (zlecenie `completed`/`cancelled` lub niezgodna `version`), 422 (brak `reason`). */
    @Transactional
    public LabOrderResponse cancel(String orderId, OrderCancelRequest request) {
        return transition(orderId, OrderStatus.CANCELLED, request.reason().trim(), request.version());
    }

    // --- pomocnicze ---

    private LabOrderResponse transition(String orderId, OrderStatus target, String note, Long expectedVersion) {
        UUID actor = actor();
        LabOrder order = require(orderId);
        if (expectedVersion != null && expectedVersion != order.getVersion()) {
            throw new ConflictException("Zlecenie zostalo zmodyfikowane przez inna osobe (wersja "
                    + order.getVersion() + "); odswiez dane i sprobuj ponownie");
        }
        OrderStatus previous = order.getStatus();
        if (!LabOrderStateMachine.canTransition(previous, target)) {
            throw new ConflictException("Niedozwolone przejscie statusu zlecenia z '" + previous.wire() + "' na '"
                    + target.wire() + "'");
        }
        Instant now = Instant.now();
        order.transitionTo(target, now, actor, note);
        LabOrder saved = orders.saveAndFlush(order);
        events.publishEvent(new LabOrderStatusChanged(saved.getId(), saved.getPatientId(), saved.getOrderedById(),
                previous, target, now, actor, note));
        return LabOrderMapper.toResponse(saved);
    }

    /** `update-status` - dowolny dozwolony status; `collect-specimen` (pielegniarka) - tylko `specimen_collected`. */
    private static void requireMayChangeTo(OrderStatus target, Collection<? extends GrantedAuthority> authorities) {
        Set<String> granted = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (granted.contains(UPDATE_STATUS)) {
            return;
        }
        if (granted.contains(COLLECT_SPECIMEN) && target == OrderStatus.SPECIMEN_COLLECTED) {
            return;
        }
        throw new ForbiddenException("Rola moze ustawic wylacznie status 'specimen_collected'");
    }

    /** Pozycje z snapshotem z katalogu; bledy pol (`items[i].testCode`, `items[i].specimenType`, `fasting`) do listy. */
    private List<LabOrderItem> buildItems(LabOrderCreateRequest request, List<FieldError> errors) {
        List<LabOrderItemRequest> requested = request.items();
        Set<String> codes = new HashSet<>();
        requested.forEach(i -> codes.add(i.testCode().trim()));
        Map<String, LabTest> catalog = tests.findAllById(codes).stream()
                .collect(Collectors.toMap(LabTest::getCode, Function.identity()));

        List<LabOrderItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean fastingMissing = false;
        for (int i = 0; i < requested.size(); i++) {
            LabOrderItemRequest item = requested.get(i);
            String code = item.testCode().trim();
            LabTest test = catalog.get(code);
            if (test == null) {
                errors.add(new FieldError("items[" + i + "].testCode",
                        "Badanie o kodzie '" + code + "' nie istnieje w katalogu", "notFound"));
                continue;
            }
            if (!seen.add(code)) {
                errors.add(new FieldError("items[" + i + "].testCode",
                        "Badanie '" + code + "' wystepuje na zleceniu wielokrotnie", "duplicate"));
                continue;
            }
            if (!test.getSpecimenTypes().contains(item.specimenType())) {
                errors.add(new FieldError("items[" + i + "].specimenType", "Material '" + item.specimenType().wire()
                        + "' nie jest dozwolony dla badania '" + code + "'", "notAllowed"));
                continue;
            }
            fastingMissing |= test.isFastingRequired();
            items.add(LabOrderItem.of(test.getCode(), test.getName(), item.specimenType()));
        }
        if (fastingMissing && !Boolean.TRUE.equals(request.fasting())) {
            errors.add(new FieldError("fasting", "Zlecone badanie wymaga pobrania na czczo", "fastingRequired"));
        }
        return items;
    }

    private LabOrder require(String orderId) {
        UUID id = parse(orderId);
        return (id == null ? java.util.Optional.<LabOrder>empty() : orders.findById(id))
                .orElseThrow(() -> NotFoundException.of("Zlecenie laboratoryjne", orderId));
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
