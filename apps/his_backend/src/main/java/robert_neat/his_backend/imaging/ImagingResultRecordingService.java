package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.staff.StaffMemberRepository;

/**
 * Wewnetrzny zapis wynikow badan obrazowych; wspolny dla `POST /fhir/DiagnosticReport` (`e-imaging`) i natywnego
 * `POST /api/v1/imaging-orders/{orderId}/results` (radiolog, {@code imaging-result:write}).
 * <ul>
 *   <li>422: brakujace/niespojne pola, nieznany pacjent/zlecenie/radiolog, niezgodnosc pacjenta lub modalnosci ze
 *       zleceniem, brak snapshotu dla wyniku zewnetrznego;</li>
 *   <li>409: zlecenie w stanie, w ktorym wynik nie moze powstac (przyjmuje `scheduled`/`in_progress`) albo zlecenie ma
 *       juz wynik ostateczny (`final`);</li>
 *   <li>zdarzenie {@link ImagingResultRecorded} (`critical` = flaga radiologa);</li>
 *   <li>auto-`completed`: wynik `final` przenosi zlecenie do `completed` jak przy recznej zmianie statusu (wpis
 *       historii, aktor = rejestrujacy lub systemowy, {@link ImagingOrderStatusChanged}); wynik `preliminary` nie.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ImagingResultRecordingService {

    private final ImagingResultRepository results;
    private final ImagingOrderRepository orders;
    private final PatientRepository patients;
    private final StaffMemberRepository staff;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    ImagingResultRecordingService(ImagingResultRepository results, ImagingOrderRepository orders,
            PatientRepository patients, StaffMemberRepository staff, CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.results = results;
        this.orders = orders;
        this.patients = patients;
        this.staff = staff;
        this.currentActor = currentActor;
        this.events = events;
    }

    @Transactional
    public ImagingResultResponse recordResult(RecordImagingResultCommand cmd) {
        UUID actor = currentActor.staffId().orElse(null);
        List<FieldError> errors = new ArrayList<>();
        validateHeader(cmd, errors);

        ImagingOrder order = null;
        if (cmd.orderId() != null) {
            order = orders.findById(cmd.orderId()).orElse(null);
            if (order == null) {
                errors.add(new FieldError("orderId", "Zlecenie nie istnieje", "notFound"));
            } else {
                if (cmd.patientId() != null && !order.getPatientId().equals(cmd.patientId())) {
                    errors.add(new FieldError("orderId", "Zlecenie dotyczy innego pacjenta", "mismatch"));
                }
                if (cmd.modality() != null && cmd.modality() != order.getModality()) {
                    errors.add(new FieldError("modality", "Modalnosc zlecenia to '" + order.getModality().wire()
                            + "'", "mismatch"));
                }
            }
        }
        ImagingModality modality = order != null ? order.getModality() : cmd.modality();
        String examName = order != null ? order.getExamName() : blankToNull(cmd.examName());
        String bodyRegion = order != null ? order.getBodyRegion() : blankToNull(cmd.bodyRegion());
        if (cmd.orderId() == null) {
            requireField(modality, "modality", errors);
            requireField(examName, "examName", errors);
            requireField(bodyRegion, "bodyRegion", errors);
            requireMax(examName, 200, "examName", errors);
            requireMax(bodyRegion, 100, "bodyRegion", errors);
        }
        UUID radiologistId = cmd.radiologistId();
        if (radiologistId != null && !staff.existsById(radiologistId)) {
            errors.add(new FieldError("radiologistId", "Pracownik nie istnieje", "notFound"));
        }
        String radiologistName = blankToNull(cmd.radiologistName());
        requireMax(radiologistName, 200, "radiologistName", errors);
        if (radiologistName == null && errors.isEmpty()) {
            UUID whose = radiologistId != null ? radiologistId : actor;
            radiologistName = whose == null ? null : staffName(whose).orElse(null);
            if (radiologistName == null) {
                errors.add(new FieldError("radiologistName",
                        "Radiolog jest wymagany (brak nazwy i zalogowanego pracownika)", "required"));
            }
            if (radiologistId == null) {
                radiologistId = actor;
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        if (order != null) {
            requireOrderAcceptsResult(order);
            if (results.existsByOrderIdAndStatus(order.getId(), ImagingResultStatus.FINAL)) {
                throw new ConflictException("Zlecenie ma juz wynik ostateczny ('final')");
            }
        }

        Instant now = Instant.now();
        ImagingResult saved = results.saveAndFlush(ImagingResult.record(cmd.patientId(),
                order == null ? null : order.getId(), modality, examName, bodyRegion, cmd.performedAt(),
                cmd.reportedAt(), radiologistName, radiologistId, blankToNull(cmd.technique()),
                cmd.findings().trim(), cmd.conclusion().trim(), cmd.status(),
                cmd.imageCount() == null ? 0 : cmd.imageCount(), cmd.critical()));

        events.publishEvent(new ImagingResultRecorded(saved.getId(), saved.getPatientId(), saved.getOrderId(),
                order == null ? null : order.getOrderedById(), saved.getModality(), saved.getStatus(),
                saved.isCritical(), now, actor));

        if (order != null && cmd.status() == ImagingResultStatus.FINAL) {
            completeOrder(order, now, actor);
        }
        return ImagingResultMapper.toResponse(saved);
    }

    // --- walidacja ---

    private void validateHeader(RecordImagingResultCommand cmd, List<FieldError> errors) {
        if (cmd.patientId() == null) {
            errors.add(new FieldError("patientId", "Pacjent jest wymagany", "required"));
        } else if (!patients.existsById(cmd.patientId())) {
            errors.add(new FieldError("patientId", "Pacjent nie istnieje", "notFound"));
        }
        requireField(cmd.performedAt(), "performedAt", errors);
        requireField(cmd.reportedAt(), "reportedAt", errors);
        if (cmd.performedAt() != null && cmd.reportedAt() != null && cmd.reportedAt().isBefore(cmd.performedAt())) {
            errors.add(new FieldError("reportedAt", "Data opisu nie moze byc wczesniejsza niz data wykonania badania",
                    "range"));
        }
        requireField(blankToNull(cmd.findings()), "findings", errors);
        requireField(blankToNull(cmd.conclusion()), "conclusion", errors);
        requireField(cmd.status(), "status", errors);
        if (cmd.imageCount() != null && cmd.imageCount() < 0) {
            errors.add(new FieldError("imageCount", "Liczba obrazow nie moze byc ujemna", "range"));
        }
    }

    private static void requireField(Object value, String field, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Pole jest wymagane", "required"));
        }
    }

    private static void requireMax(String value, int max, String field, List<FieldError> errors) {
        if (value != null && value.length() > max) {
            errors.add(new FieldError(field, "Wartosc jest zbyt dluga (max " + max + ")", "size"));
        }
    }

    /** 409: zlecenie musi byc zaplanowane lub w toku (nie `ordered`, `completed`, `cancelled`). */
    private static void requireOrderAcceptsResult(ImagingOrder order) {
        OrderStatus current = order.getStatus();
        if (!ImagingOrderStateMachine.acceptsResult(current)) {
            throw new ConflictException("Zlecenie w statusie '" + current.wire() + "' nie przyjmuje wyniku");
        }
    }

    // --- auto-completed ---

    private void completeOrder(ImagingOrder order, Instant now, UUID actor) {
        OrderStatus previous = order.getStatus();
        if (!ImagingOrderStateMachine.canTransition(previous, OrderStatus.COMPLETED)) {
            return;
        }
        String note = "Wynik ostateczny zapisany (automatycznie)";
        order.transitionTo(OrderStatus.COMPLETED, now, actor, note);
        ImagingOrder saved = orders.saveAndFlush(order);
        events.publishEvent(new ImagingOrderStatusChanged(saved.getId(), saved.getPatientId(),
                saved.getOrderedById(), previous, OrderStatus.COMPLETED, now, actor, note));
    }

    // --- pomocnicze ---

    private Optional<String> staffName(UUID staffId) {
        return staff.findById(staffId)
                .map(s -> (s.getTitle() + " " + s.getFirstName() + " " + s.getLastName()).trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
