package robert_neat.his_backend.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import robert_neat.his_backend.alert.events.AlertAcknowledged;
import robert_neat.his_backend.alert.events.AlertCreated;
import robert_neat.his_backend.common.api.CursorPage;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.Admission;
import robert_neat.his_backend.patient.AdmissionRecordStatus;
import robert_neat.his_backend.patient.AdmissionRepository;

/**
 * Alerty kliniczne. Odczyt i potwierdzenie: stan potwierdzenia jest per uzytkownik ({@link AlertAcknowledgement}),
 * `acknowledged` w odpowiedzi to projekcja dla zalogowanego (aktor z tokenu). Lista jest paginowana kursorem
 * (`before=createdAt`, malejaco); domyslny/maksymalny rozmiar strony: {@link #DEFAULT_PAGE_SIZE}/
 * {@link #MAX_PAGE_SIZE}. Alerty widoczne sa dla wszystkich z `alert:read` (model alertu z ERD nie ma
 * adresata; adresaci i oddzial trafiaja tylko do zdarzenia {@link AlertCreated}, dla warstwy push).
 * Tworzenie ({@link #raise}) wylacznie dla {@link AlertEventListener}, w transakcji zdarzenia zrodlowego.
 */
@Service
@Transactional(readOnly = true)
public class AlertService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final ClinicalAlertRepository alerts;
    private final AlertAcknowledgementRepository acknowledgements;
    private final AdmissionRepository admissions;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    AlertService(ClinicalAlertRepository alerts, AlertAcknowledgementRepository acknowledgements,
            AdmissionRepository admissions, CurrentActor currentActor, ApplicationEventPublisher events) {
        this.alerts = alerts;
        this.acknowledgements = acknowledgements;
        this.admissions = admissions;
        this.currentActor = currentActor;
        this.events = events;
    }

    /**
     * `acknowledged` filtruje wzgledem zalogowanego uzytkownika (brak filtra = wszystkie). Kursor `before`
     * ogranicza do alertow starszych niz podany znacznik czasu (wylacznie); `size` poza [1, {@link #MAX_PAGE_SIZE}]
     * jest przycinane ({@code null}/`<= 0` -> {@link #DEFAULT_PAGE_SIZE}, `> MAX_PAGE_SIZE` -> `MAX_PAGE_SIZE`).
     */
    public CursorPage<AlertResponse> list(UUID patientId, Boolean acknowledged, Instant before, Integer size) {
        UUID me = actor();
        int pageSize = clampSize(size);
        List<Specification<ClinicalAlert>> parts = new ArrayList<>();
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (acknowledged != null) {
            parts.add((root, query, cb) -> {
                var exists = cb.exists(acknowledgedBy(root, query.subquery(Integer.class), cb, me));
                return acknowledged ? exists : cb.not(exists);
            });
        }
        if (before != null) {
            parts.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), before));
        }
        List<ClinicalAlert> found = alerts
                .findAll(Specification.allOf(parts), PageRequest.of(0, pageSize + 1, NEWEST_FIRST)).getContent();
        CursorPage<ClinicalAlert> page = CursorPage.of(found, pageSize, ClinicalAlert::getCreatedAt);
        Map<UUID, AlertAcknowledgement> mine = acknowledgementsOf(me, page.items());
        List<AlertResponse> items = page.items().stream().map(a -> AlertMapper.toResponse(a, mine.get(a.getId())))
                .toList();
        return new CursorPage<>(items, page.nextBefore());
    }

    private static int clampSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /** 404 alert; 403 brak powiazania sesji z pracownikiem. Idempotentne: ponowne potwierdzenie nic nie zmienia. */
    @Transactional
    public AlertResponse acknowledge(String alertId) {
        UUID me = actor();
        UUID id = parse(alertId);
        ClinicalAlert alert = (id == null ? Optional.<ClinicalAlert>empty() : alerts.findById(id))
                .orElseThrow(() -> NotFoundException.of("Alert", alertId));
        acknowledgements.acknowledge(alert.getId(), me, Instant.now());
        AlertAcknowledgement ack = acknowledgements.findByStaffAndAlert(me, alert.getId()).orElseThrow();
        AlertResponse response = AlertMapper.toResponse(alert, ack);
        events.publishEvent(new AlertAcknowledged(me, response));
        return response;
    }

    /**
     * Tworzy alert i publikuje {@link AlertCreated} - ZAWSZE w transakcji zdarzenia zrodlowego (`MANDATORY`), wiec
     * alert powstaje atomowo ze zmiana, ktora go wywolala. `directRecipients` (zlecajacy / osoba przypisana) i - gdy
     * `notifyAttending` - lekarz prowadzacy z aktywnego przyjecia pacjenta trafiaja do zdarzenia jako adresaci.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClinicalAlert raise(AlertType type, AlertSeverity severity, UUID patientId, String message,
            AlertTarget target, List<UUID> directRecipients, boolean notifyAttending) {
        Instant now = Instant.now();
        ClinicalAlert saved = alerts
                .saveAndFlush(ClinicalAlert.raise(type, severity, patientId, message, now, target));
        Optional<Admission> active = patientId == null ? Optional.empty()
                : admissions.findByPatientIdAndStatus(patientId, AdmissionRecordStatus.ACTIVE);
        Set<UUID> recipients = new LinkedHashSet<>();
        directRecipients.stream().filter(r -> r != null).forEach(recipients::add);
        if (notifyAttending) {
            active.map(Admission::getAttendingPhysicianId).ifPresent(recipients::add);
        }
        events.publishEvent(new AlertCreated(saved.getId(), type, severity, patientId, message, now, target,
                active.map(Admission::getWardId).orElse(null), List.copyOf(recipients)));
        return saved;
    }

    private Map<UUID, AlertAcknowledgement> acknowledgementsOf(UUID me, List<ClinicalAlert> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        return acknowledgements.findByStaffAndAlerts(me, found.stream().map(ClinicalAlert::getId).toList()).stream()
                .collect(Collectors.toMap(AlertAcknowledgement::alertId, Function.identity()));
    }

    /** Liczba alertow `critical` niepotwierdzonych przez pracownika (licznik `criticalAlerts` dashboardu). */
    public long countUnacknowledgedCritical(UUID staffId) {
        return alerts.count((root, query, cb) -> cb.and(cb.equal(root.get("severity"), AlertSeverity.CRITICAL),
                cb.not(cb.exists(acknowledgedBy(root, query.subquery(Integer.class), cb, staffId)))));
    }

    /** Podzapytanie "alert potwierdzony przez pracownika". */
    private static Subquery<Integer> acknowledgedBy(Root<ClinicalAlert> root, Subquery<Integer> sub,
            jakarta.persistence.criteria.CriteriaBuilder cb, UUID staffId) {
        Root<AlertAcknowledgement> ack = sub.from(AlertAcknowledgement.class);
        return sub.select(cb.literal(1)).where(cb.equal(ack.get("id").get("alertId"), root.get("id")),
                cb.equal(ack.get("id").get("staffId"), staffId));
    }

    /** Identyfikator jest nieprzezroczysty dla klienta: niepoprawny format = "nie istnieje" (404). */
    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID actor() {
        return currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
    }
}
