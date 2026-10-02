package robert_neat.his_backend.alert;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;
import robert_neat.his_backend.messaging.Priority;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.vitals.VitalAnomaly;
import robert_neat.his_backend.vitals.events.VitalAnomalyDetected;

/**
 * Zdarzenia przekrojowe (API.md, par. 14) -> alerty. Faza: SYNCHRONICZNY `@EventListener` w transakcji publikujacego
 * (zdarzenia domenowe sa publikowane w transakcji zapisu), a {@link AlertService#raise} ma `MANDATORY`. Alert powstaje
 * wiec atomowo ze zmiana zrodlowa: blad tworzenia alertu wycofuje takze zapis zrodlowy. `BEFORE_COMMIT` dawalby to
 * samo, ale nie zadzialalby w testach z rollbackiem (nie ma commitu), a synchroniczny listener uruchamia sie zawsze.
 * Push po commit: `@TransactionalEventListener(AFTER_COMMIT)` na {@link robert_neat.his_backend.alert.events.AlertCreated}.
 */
@Component
class AlertEventListener {

    private final AlertService alerts;
    private final PatientRepository patients;

    AlertEventListener(AlertService alerts, PatientRepository patients) {
        this.alerts = alerts;
        this.patients = patients;
    }

    /** Wynik laboratoryjny z flaga LL/HH -> `critical_result`/`critical`, adresaci: zlecajacy + lekarz prowadzacy. */
    @EventListener
    void on(LabResultRecorded e) {
        if (!e.critical()) {
            return;
        }
        // Kody analitow rowne kodowi badania pomijamy w nawiasie - dla badan jednoanalitowych (np. TROP, CRP)
        // kod analitu jest identyczny z kodem badania, co dawalo zdublowany tekst "TROP (TROP)".
        List<String> distinctCodes = e.criticalAnalyteCodes() == null ? List.of()
                : e.criticalAnalyteCodes().stream().filter(code -> !code.equals(e.testCode())).toList();
        String codes = distinctCodes.isEmpty() ? "" : " (" + String.join(", ", distinctCodes) + ")";
        alerts.raise(AlertType.CRITICAL_RESULT, AlertSeverity.CRITICAL, e.patientId(),
                "Krytyczny wynik badania laboratoryjnego " + e.testCode() + codes + " - " + patient(e.patientId())
                        + ".",
                new AlertTarget(AlertTargetKind.LAB_RESULT, e.resultId(), e.patientId()),
                Arrays.asList(e.orderedById()), true);
    }

    /** Wynik obrazowy z `critical:true` -> `critical_result`/`critical`, cel `imaging_result`. */
    @EventListener
    void on(ImagingResultRecorded e) {
        if (!e.critical()) {
            return;
        }
        alerts.raise(AlertType.CRITICAL_RESULT, AlertSeverity.CRITICAL, e.patientId(),
                "Krytyczny wynik badania obrazowego (" + e.modality().wire() + ") - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.IMAGING_RESULT, e.resultId(), e.patientId()),
                Arrays.asList(e.orderedById()), true);
    }

    /** Krytyczna anomalia parametrow zyciowych -> `vital_anomaly`/`critical`, cel `patient_vitals` (id = pacjent). */
    @EventListener
    void on(VitalAnomalyDetected e) {
        String details = e.anomalies().stream().map(VitalAnomaly::message).collect(Collectors.joining(" "));
        alerts.raise(AlertType.VITAL_ANOMALY, AlertSeverity.CRITICAL, e.patientId(),
                "Krytyczne parametry życiowe - " + patient(e.patientId()) + ". " + details,
                new AlertTarget(AlertTargetKind.PATIENT_VITALS, e.patientId(), null),
                List.of(), true);
    }

    /** Zlecenie laboratoryjne zakonczone/anulowane -> `order_status`/`info` dla zlecajacego. */
    @EventListener
    void on(LabOrderStatusChanged e) {
        orderStatus(e.status(), "laboratoryjne", AlertTargetKind.LAB_ORDER, e.orderId(), e.patientId(),
                e.orderedById(), e.note());
    }

    /** Zlecenie obrazowe zakonczone/anulowane -> `order_status`/`info` dla zlecajacego. */
    @EventListener
    void on(ImagingOrderStatusChanged e) {
        orderStatus(e.status(), "obrazowe", AlertTargetKind.IMAGING_ORDER, e.orderId(), e.patientId(),
                e.orderedById(), e.note());
    }

    /** Przypisanie zadania -> `task` dla osoby przypisanej (`normal` = info, `high`/`critical` = warning). */
    @EventListener
    void on(TaskAssigned e) {
        AlertSeverity severity = e.priority() == Priority.NORMAL ? AlertSeverity.INFO : AlertSeverity.WARNING;
        String about = e.patientId() == null ? "" : " (" + patient(e.patientId()) + ")";
        alerts.raise(AlertType.TASK, severity, e.patientId(), "Nowe zadanie: " + e.title() + about + ".",
                new AlertTarget(AlertTargetKind.TASK, e.taskId(), e.patientId()), List.of(e.assignedToId()), false);
    }

    private void orderStatus(OrderStatus status, String kind, AlertTargetKind targetKind, UUID orderId,
            UUID patientId, UUID orderedById, String note) {
        if (status != OrderStatus.COMPLETED && status != OrderStatus.CANCELLED) {
            return;
        }
        String verb = status == OrderStatus.COMPLETED ? "Zakończono" : "Anulowano";
        String reason = status == OrderStatus.CANCELLED && note != null && !note.isBlank()
                ? " Powód: " + note.trim() : "";
        alerts.raise(AlertType.ORDER_STATUS, AlertSeverity.INFO, patientId,
                verb + " zlecenie " + kind + " - " + patient(patientId) + "." + reason,
                new AlertTarget(targetKind, orderId, patientId), Arrays.asList(orderedById), false);
    }

    /** `pacjent Imie Nazwisko` (bez płci w modelu alertu) albo neutralny zapis, gdy pacjenta brak. */
    private String patient(UUID patientId) {
        return patients.findById(patientId).map(p -> "pacjent " + p.getFirstName() + " " + p.getLastName())
                .orElse("pacjent");
    }
}
