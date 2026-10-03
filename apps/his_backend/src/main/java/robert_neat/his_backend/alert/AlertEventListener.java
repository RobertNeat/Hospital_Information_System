package robert_neat.his_backend.alert;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.ehr.events.AllergyRecorded;
import robert_neat.his_backend.ehr.events.ClinicalNoteCreated;
import robert_neat.his_backend.ehr.events.DiagnosisRecorded;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;
import robert_neat.his_backend.imaging.events.ImagingResultRecorded;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;
import robert_neat.his_backend.lab.events.LabResultRecorded;
import robert_neat.his_backend.messaging.Priority;
import robert_neat.his_backend.messaging.TeamTask;
import robert_neat.his_backend.messaging.TeamTaskRepository;
import robert_neat.his_backend.messaging.TaskStatus;
import robert_neat.his_backend.messaging.events.MessageSent;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.messaging.events.TaskStatusChanged;
import robert_neat.his_backend.patient.Admission;
import robert_neat.his_backend.patient.AdmissionRepository;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.events.PatientAdmitted;
import robert_neat.his_backend.patient.events.PatientDischarged;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;
import robert_neat.his_backend.prescription.events.PrescriptionIssued;
import robert_neat.his_backend.vitals.AnomalySeverity;
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
    private final TeamTaskRepository tasks;
    private final AdmissionRepository admissions;

    AlertEventListener(AlertService alerts, PatientRepository patients, TeamTaskRepository tasks,
            AdmissionRepository admissions) {
        this.alerts = alerts;
        this.patients = patients;
        this.tasks = tasks;
        this.admissions = admissions;
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

    /**
     * Anomalia parametrow zyciowych -> `vital_anomaly`, cel `patient_vitals` (id = pacjent). Co najmniej jedna
     * anomalia `critical` w zdarzeniu -> alert `critical`, w przeciwnym razie (same `warning`) -> `warning`.
     */
    @EventListener
    void on(VitalAnomalyDetected e) {
        boolean critical = e.anomalies().stream().anyMatch(a -> a.severity() == AnomalySeverity.CRITICAL);
        AlertSeverity severity = critical ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        String prefix = critical ? "Krytyczne parametry życiowe" : "Nieprawidłowe parametry życiowe";
        String details = e.anomalies().stream().map(VitalAnomaly::message).collect(Collectors.joining(" "));
        alerts.raise(AlertType.VITAL_ANOMALY, severity, e.patientId(),
                prefix + " - " + patient(e.patientId()) + ". " + details,
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

    /**
     * Przyjecie pacjenta -> `system`/`info`, cel `patient` (id = pacjent). Przyjecie jest juz zapisane (`saveAndFlush`
     * w tej samej transakcji) w chwili publikacji, wiec `notifyAttending=true` znajduje je jako `ACTIVE` i dolicza
     * lekarza prowadzacego - bez potrzeby podawania go wprost.
     */
    @EventListener
    void on(PatientAdmitted e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, e.patientId(),
                "Przyjęto pacjenta - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), List.of(), true);
    }

    /**
     * Wypis pacjenta -> `system`/`info`, cel `patient`. W chwili publikacji przyjecie jest juz `DISCHARGED`
     * (zapisane przed zdarzeniem), wiec `notifyAttending` z {@link AlertService#raise} nic by nie znalazl - lekarza
     * prowadzacego i oddzial (dla `/topic/alerts/{wardId}`) pobieramy wprost z wlasnie zamknietego przyjecia.
     */
    @EventListener
    void on(PatientDischarged e) {
        UUID attending = admissions.findById(e.admissionId()).map(Admission::getAttendingPhysicianId).orElse(null);
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, e.patientId(),
                "Wypisano pacjenta - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), Arrays.asList(attending), false,
                e.wardId());
    }

    /** Nowa notatka kliniczna -> `system`/`info`, cel `patient`; adresat: lekarz prowadzacy (jesli jest). */
    @EventListener
    void on(ClinicalNoteCreated e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, e.patientId(),
                "Nowa notatka kliniczna (" + e.category().wire() + ") - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), List.of(), true);
    }

    /** Nowe rozpoznanie -> `system`/`info`, cel `patient`; adresat: lekarz prowadzacy. */
    @EventListener
    void on(DiagnosisRecorded e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, e.patientId(),
                "Zapisano rozpoznanie " + e.code().display() + " - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), List.of(), true);
    }

    /** Nowa alergia -> `system`/`warning` (istotne klinicznie), cel `patient`; adresat: lekarz prowadzacy. */
    @EventListener
    void on(AllergyRecorded e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.WARNING, e.patientId(),
                "Odnotowano alergię: " + e.substance() + " - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), List.of(), true);
    }

    /**
     * Wystawiono recepte -> `system`/`info`, cel `patient`; bez adresatow bezposrednich - wystawiajacy jest tym
     * samym aktorem, ktory wlasnie wykonal zapis (`prescriberId == actor` w {@code PrescriptionService#issue}),
     * wiec nie ma kogo dodatkowo powiadamiac; widoczny dla wszystkich z `alert:read` i na `/topic/alerts/{wardId}`.
     */
    @EventListener
    void on(PrescriptionIssued e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, e.patientId(),
                "Wystawiono receptę - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), List.of(), false);
    }

    /**
     * Anulowano recepte -> `system`/`warning`, cel `patient`; adresat: wystawiajacy, ale tylko gdy to NIE on
     * anulowal (`actorId == prescriberId` -> zbedne powiadomienie o wlasnej akcji). Anulowanie przychodzace z
     * e-receipt ma `actorId == null`, wiec zawsze powiadamia wystawiajacego.
     */
    @EventListener
    void on(PrescriptionCancelled e) {
        List<UUID> recipients = e.prescriberId() != null && !e.prescriberId().equals(e.actorId())
                ? Arrays.asList(e.prescriberId()) : List.of();
        alerts.raise(AlertType.SYSTEM, AlertSeverity.WARNING, e.patientId(),
                "Anulowano receptę - " + patient(e.patientId()) + ".",
                new AlertTarget(AlertTargetKind.PATIENT, e.patientId(), null), recipients, false);
    }

    /**
     * Wiadomosc -> `system`/`info`, bez adresatow, bez celu i bez `patientId` (`directRecipients=List.of()`,
     * `notifyAttending=false`): alerty nie maja adresata w modelu (widoczne dla kazdego z `alert:read`), wiec tresc
     * (temat watku, pacjent) tutaj byla bezplatnym rozgloszeniem prywatnej korespondencji - nie zamieszczamy jej.
     * Dostarczenie i licznik nieprzeczytanych dla uczestnikow juz zapewnia
     * {@link robert_neat.his_backend.realtime.RealtimePublisher} (push `/user/queue/messages` + `/user/queue/threads`);
     * ten wpis sluzy wylacznie ogolnej, nieadresowanej widoczności "byla nowa wiadomość" w historii alertow.
     */
    @EventListener
    void on(MessageSent e) {
        alerts.raise(AlertType.SYSTEM, AlertSeverity.INFO, null, "Wysłano nową wiadomość.", null, List.of(), false);
    }

    /**
     * Zmiana statusu zadania -> `task`/`info`, tylko dla stanow koncowych (`done`/`cancelled`, analogicznie do
     * {@link #orderStatus}); stany posrednie (`in_progress`) nie generuja alertu. Adresat: druga strona niz aktor
     * zmiany (jesli to przypisany zmienil status - powiadamiamy tworce zadania i odwrotnie); zywy push
     * `/user/queue/tasks` dla przypisanego juz zapewnia {@link robert_neat.his_backend.realtime.RealtimePublisher}.
     */
    @EventListener
    void on(TaskStatusChanged e) {
        if (e.status() != TaskStatus.DONE && e.status() != TaskStatus.CANCELLED) {
            return;
        }
        UUID other = e.actorId() != null && e.actorId().equals(e.assignedToId()) ? e.createdById() : e.assignedToId();
        String verb = e.status() == TaskStatus.DONE ? "Zakończono" : "Anulowano";
        TeamTask task = tasks.findById(e.taskId()).orElse(null);
        String title = task == null ? "" : " \"" + task.getTitle() + "\"";
        alerts.raise(AlertType.TASK, AlertSeverity.INFO, null, verb + " zadanie" + title + ".",
                new AlertTarget(AlertTargetKind.TASK, e.taskId(), null), Arrays.asList(other), false);
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
