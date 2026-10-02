package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.LabAnalyteDefinition;
import robert_neat.his_backend.catalog.LabTest;
import robert_neat.his_backend.catalog.LabTestRepository;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.lab.RecordLabResultCommand.ObservationInput;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.staff.StaffMemberRepository;

/**
 * Wewnetrzny zapis wynikow laboratoryjnych; wspolny dla `POST /fhir/DiagnosticReport` (`e-laboratory`) i natywnego
 * `POST /api/v1/lab-orders/{orderId}/results` (laborant, {@code lab-result:write}).
 * <ul>
 *   <li>422: brakujace/niespojne pola, nieznany pacjent/zlecenie/pozycja/badanie/analit, niezgodnosc pacjenta,
 *       badania lub pozycji ze zleceniem, dokladnie jedna wartosc na obserwacje;</li>
 *   <li>409: zlecenie w stanie, w ktorym wynik nie moze powstac (`ordered`/`scheduled`/`cancelled`; `completed` -
 *       tylko korekta `corrected`) albo pozycja ma juz wynik ostateczny (wtedy dozwolona tylko korekta);</li>
 *   <li>flaga obserwacji: jawna z polecenia, inaczej N/L/H z zakresu katalogu (LL/HH tylko jawnie);</li>
 *   <li>zdarzenie {@link LabResultRecorded} (`critical` gdy LL/HH);</li>
 *   <li>auto-`completed`: gdy wynik jest zatwierdzony (`final`/`corrected`), a WSZYSTKIE pozycje zlecenia
 *       (`specimen_collected`/`in_progress`) maja zatwierdzony wynik, zlecenie przechodzi do `completed` jak przy
 *       recznej zmianie statusu (wpis historii, aktor = rejestrujacy lub systemowy, {@link LabOrderStatusChanged}).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LabResultRecordingService {

    private static final Set<ResultStatus> FINALISED = EnumSet.of(ResultStatus.FINAL, ResultStatus.CORRECTED);
    private static final BigDecimal MAX_ABS = new BigDecimal("1E10"); // numeric(14,4)

    private final LabResultRepository results;
    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final StaffMemberRepository staff;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    LabResultRecordingService(LabResultRepository results, LabOrderRepository orders, LabTestRepository tests,
            PatientRepository patients, StaffMemberRepository staff, CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.results = results;
        this.orders = orders;
        this.tests = tests;
        this.patients = patients;
        this.staff = staff;
        this.currentActor = currentActor;
        this.events = events;
    }

    @Transactional
    public LabResultResponse recordResult(RecordLabResultCommand cmd) {
        UUID actor = currentActor.staffId().orElse(null);
        List<FieldError> errors = new ArrayList<>();
        validateHeader(cmd, errors);

        boolean testGiven = cmd.testCode() != null && !cmd.testCode().isBlank();
        LabTest test = testGiven ? tests.findById(cmd.testCode().trim()).orElse(null) : null;
        if (testGiven && test == null) {
            errors.add(new FieldError("testCode", "Badanie o kodzie '" + cmd.testCode() + "' nie istnieje w katalogu",
                    "notFound"));
        }
        OrderRef ref = resolveOrder(cmd, errors);
        List<LabObservation> observations = test == null ? List.of() : buildObservations(cmd, test, errors);
        if (ref.order() != null && ref.item() != null && test != null) {
            if (!ref.order().getPatientId().equals(cmd.patientId())) {
                errors.add(new FieldError("orderId", "Zlecenie dotyczy innego pacjenta", "mismatch"));
            }
            if (!ref.item().getTestCode().equals(test.getCode())) {
                errors.add(new FieldError("testCode", "Pozycja zlecenia dotyczy badania '"
                        + ref.item().getTestCode() + "'", "mismatch"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        LabOrder order = ref.order();
        LabOrderItem item = ref.item();
        if (order != null) {
            requireOrderAcceptsResult(order, cmd.status());
            if (cmd.status() != ResultStatus.CORRECTED
                    && results.existsByOrderItemIdAndStatusIn(item.getId(), FINALISED)) {
                throw new ConflictException("Pozycja zlecenia ma juz wynik ostateczny; dozwolona tylko korekta ('corrected')");
            }
        }

        Instant now = Instant.now();
        LabResult saved = results.saveAndFlush(LabResult.record(cmd.patientId(),
                order == null ? null : order.getId(), item == null ? null : item.getId(), test.getCode(),
                test.getName(), test.getCategory(), cmd.collectedAt(), cmd.resultedAt(), cmd.status(),
                performerName(cmd.performerName(), actor), blankToNull(cmd.comment()), observations));

        List<String> critical = observations.stream().filter(o -> o.getFlag().isCritical())
                .map(LabObservation::getAnalyteCode).toList();
        events.publishEvent(new LabResultRecorded(saved.getId(), saved.getPatientId(), saved.getOrderId(),
                order == null ? null : order.getOrderedById(), saved.getTestCode(), saved.getStatus(),
                !critical.isEmpty(), critical, now, actor));

        if (order != null && cmd.status().isFinalised()) {
            completeOrderIfAllItemsFinalised(order, now, actor);
        }
        return LabResultMapper.toResponse(saved);
    }

    // --- walidacja ---

    private void validateHeader(RecordLabResultCommand cmd, List<FieldError> errors) {
        if (cmd.patientId() == null) {
            errors.add(new FieldError("patientId", "Pacjent jest wymagany", "required"));
        } else if (!patients.existsById(cmd.patientId())) {
            errors.add(new FieldError("patientId", "Pacjent nie istnieje", "notFound"));
        }
        if (cmd.testCode() == null || cmd.testCode().isBlank()) {
            errors.add(new FieldError("testCode", "Kod badania jest wymagany", "required"));
        }
        if (cmd.collectedAt() == null) {
            errors.add(new FieldError("collectedAt", "Data pobrania jest wymagana", "required"));
        }
        if (cmd.resultedAt() == null) {
            errors.add(new FieldError("resultedAt", "Data wyniku jest wymagana", "required"));
        }
        if (cmd.collectedAt() != null && cmd.resultedAt() != null && cmd.resultedAt().isBefore(cmd.collectedAt())) {
            errors.add(new FieldError("resultedAt", "Data wyniku nie moze byc wczesniejsza niz data pobrania",
                    "range"));
        }
        if (cmd.status() == null) {
            errors.add(new FieldError("status", "Status wyniku jest wymagany", "required"));
        }
        if (cmd.observations() == null || cmd.observations().isEmpty()) {
            errors.add(new FieldError("observations", "Wynik musi zawierac co najmniej jedna obserwacje",
                    "required"));
        }
    }

    /** Zlecenie i pozycja z polecenia; bledy referencji do `errors`, brak zlecenia = wynik zewnetrzny. */
    private OrderRef resolveOrder(RecordLabResultCommand cmd, List<FieldError> errors) {
        LabOrder order = null;
        LabOrderItem item = null;
        if (cmd.orderItemId() != null) {
            order = orders.findByItemsId(cmd.orderItemId()).orElse(null);
            if (order == null) {
                errors.add(new FieldError("orderItemId", "Pozycja zlecenia nie istnieje", "notFound"));
                return new OrderRef(null, null);
            }
            if (cmd.orderId() != null && !cmd.orderId().equals(order.getId())) {
                errors.add(new FieldError("orderItemId", "Pozycja nie nalezy do wskazanego zlecenia", "mismatch"));
                return new OrderRef(null, null);
            }
            UUID itemId = cmd.orderItemId();
            item = order.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow();
        } else if (cmd.orderId() != null) {
            order = orders.findById(cmd.orderId()).orElse(null);
            if (order == null) {
                errors.add(new FieldError("orderId", "Zlecenie nie istnieje", "notFound"));
                return new OrderRef(null, null);
            }
            String testCode = cmd.testCode() == null ? null : cmd.testCode().trim();
            item = order.getItems().stream().filter(i -> i.getTestCode().equals(testCode)).findFirst().orElse(null);
            if (item == null) {
                errors.add(new FieldError("testCode", "Zlecenie nie zawiera badania '" + testCode + "'", "mismatch"));
                return new OrderRef(null, null);
            }
        }
        return new OrderRef(order, item);
    }

    private List<LabObservation> buildObservations(RecordLabResultCommand cmd, LabTest test,
            List<FieldError> errors) {
        if (cmd.observations() == null) {
            return List.of();
        }
        Map<String, LabAnalyteDefinition> analytes = test.getAnalytes().stream()
                .collect(Collectors.toMap(LabAnalyteDefinition::getCode, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<LabObservation> built = new ArrayList<>();
        for (int i = 0; i < cmd.observations().size(); i++) {
            ObservationInput in = cmd.observations().get(i);
            String field = "observations[" + i + "]";
            if (in == null || in.analyteCode() == null) {
                errors.add(new FieldError(field + ".analyteCode", "Kod analitu jest wymagany", "required"));
                continue;
            }
            LabAnalyteDefinition def = analytes.get(in.analyteCode());
            if (def == null) {
                errors.add(new FieldError(field + ".analyteCode", "Analit '" + in.analyteCode()
                        + "' nie nalezy do badania '" + test.getCode() + "'", "notFound"));
                continue;
            }
            if (!seen.add(in.analyteCode())) {
                errors.add(new FieldError(field + ".analyteCode", "Analit '" + in.analyteCode()
                        + "' wystepuje wielokrotnie", "duplicate"));
                continue;
            }
            boolean numeric = in.numericValue() != null;
            boolean text = in.textValue() != null && !in.textValue().isBlank();
            if (numeric == text) {
                errors.add(new FieldError(field + ".value", "Wymagana dokladnie jedna wartosc: liczbowa albo tekstowa",
                        "value"));
                continue;
            }
            if (numeric && in.numericValue().abs().compareTo(MAX_ABS) >= 0) {
                errors.add(new FieldError(field + ".value", "Wartosc liczbowa poza dozwolonym zakresem", "range"));
                continue;
            }
            if (text && in.textValue().trim().length() > 500) {
                errors.add(new FieldError(field + ".value", "Wartosc tekstowa jest zbyt dluga (max 500)", "size"));
                continue;
            }
            BigDecimal value = numeric ? in.numericValue().setScale(4, RoundingMode.HALF_UP) : null;
            ObservationFlag flag = in.flag() != null ? in.flag()
                    : numeric ? ObservationFlag.fromRange(value, def.getLow(), def.getHigh()) : ObservationFlag.N;
            built.add(LabObservation.of(def.getCode(), def.getName(), value, text ? in.textValue().trim() : null,
                    def.getUnit(), new ReferenceRange(def.getLow(), def.getHigh(), null), flag));
        }
        built.sort(java.util.Comparator.comparing(LabObservation::getAnalyteCode)); // jak @OrderBy przy odczycie
        return built;
    }

    /** 409: zlecenie musi miec pobrany material i nie byc anulowane; po `completed` tylko korekta. */
    private static void requireOrderAcceptsResult(LabOrder order, ResultStatus status) {
        OrderStatus current = order.getStatus();
        boolean accepts = current == OrderStatus.SPECIMEN_COLLECTED || current == OrderStatus.IN_PROGRESS
                || (current == OrderStatus.COMPLETED && status == ResultStatus.CORRECTED);
        if (!accepts) {
            throw new ConflictException("Zlecenie w statusie '" + current.wire() + "' nie przyjmuje wyniku"
                    + (current == OrderStatus.COMPLETED ? " (dozwolona tylko korekta 'corrected')" : ""));
        }
    }

    // --- auto-completed ---

    private void completeOrderIfAllItemsFinalised(LabOrder order, Instant now, UUID actor) {
        OrderStatus previous = order.getStatus();
        if (!LabOrderStateMachine.canTransition(previous, OrderStatus.COMPLETED)) {
            return;
        }
        Set<UUID> done = new HashSet<>(results.orderItemIdsWithStatus(order.getId(), FINALISED));
        if (!order.getItems().stream().allMatch(i -> done.contains(i.getId()))) {
            return;
        }
        String note = "Wszystkie wyniki zatwierdzone (automatycznie)";
        order.transitionTo(OrderStatus.COMPLETED, now, actor, note);
        LabOrder saved = orders.saveAndFlush(order);
        events.publishEvent(new LabOrderStatusChanged(saved.getId(), saved.getPatientId(), saved.getOrderedById(),
                previous, OrderStatus.COMPLETED, now, actor, note));
    }

    // --- pomocnicze ---

    private String performerName(String requested, UUID actor) {
        String name = blankToNull(requested);
        if (name != null) {
            return name;
        }
        Optional<String> fromStaff = actor == null ? Optional.empty()
                : staff.findById(actor).map(s -> (s.getTitle() + " " + s.getFirstName() + " " + s.getLastName()).trim());
        return fromStaff.orElseThrow(() -> new ValidationFailedException("performerName",
                "Wykonawca jest wymagany (brak nazwy i zalogowanego pracownika)"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record OrderRef(LabOrder order, LabOrderItem item) {
    }
}
