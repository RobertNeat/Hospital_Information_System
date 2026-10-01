package robert_neat.his_backend.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.persistence.VersionedEntity;

/**
 * Recepta / zlecenie szpitalne (`prescription`). Zapisany status zmienia sie wylacznie przez {@link #cancel}
 * (oraz {@link #applyExternalStatus} wywolywane przez integracje e-receipt: realizacja `dispensed`/`partially_dispensed`,
 * `expired`, anulowanie).
 * <p>
 * Wygasniecie NIE jest zapisywane: {@link #effectiveStatus(LocalDate)} wylicza `expired` przy odczycie, gdy zapisany
 * status jest "zywy" (`issued`/`partially_dispensed`), a `validUntil` minelo. Zapisany `expired` (np. dane mock)
 * jest rowniez respektowany. Pozycje sa wlasnoscia agregatu (kaskada, FK `prescription_id`).
 */
@Entity
@Table(name = "prescription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "prescriber_id", nullable = false, updatable = false)
    private UUID prescriberId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private PrescriptionKind kind;

    @Column(name = "status", nullable = false, length = 25)
    private PrescriptionStatus status;

    /** Kolumna `char(4)`: 4 cyfry. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "access_code", nullable = false, updatable = false, length = 4)
    private String accessCode;

    /**
     * Kolumna `char(44)`: lokalny klucz e-recepty ({@link PrescriptionCodeGenerator}), po wystawieniu podmieniany
     * kluczem z e-receipt przez {@code PrescriptionRepository#updateERxKey} (bez zmiany `version`).
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "erx_key", nullable = false, length = 44)
    private String eRxKey;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    @OrderBy("drugName ASC, id ASC")
    @BatchSize(size = 100)
    private List<PrescriptionItem> items = new ArrayList<>();

    /** Nowa recepta w stanie `issued`. */
    static Prescription issue(UUID patientId, UUID encounterId, UUID prescriberId, Instant issuedAt,
            LocalDate validFrom, LocalDate validUntil, PrescriptionKind kind, String accessCode, String eRxKey,
            String notes, List<PrescriptionItem> items) {
        Prescription p = new Prescription();
        p.patientId = patientId;
        p.encounterId = encounterId;
        p.prescriberId = prescriberId;
        p.issuedAt = issuedAt;
        p.validFrom = validFrom;
        p.validUntil = validUntil;
        p.kind = kind;
        p.status = PrescriptionStatus.ISSUED;
        p.accessCode = accessCode;
        p.eRxKey = eRxKey;
        p.notes = notes;
        p.items.addAll(items);
        return p;
    }

    /** "Dzis" dla terminow recept: data UTC (spojnie z serializacja czasu w API). */
    public static LocalDate today() {
        return LocalDate.now(java.time.ZoneOffset.UTC);
    }

    /** Status widoczny dla klienta: zapisany, chyba ze "zywa" recepta po terminie - wtedy `expired`. */
    public PrescriptionStatus effectiveStatus(LocalDate today) {
        return status.isOpen() && validUntil.isBefore(today) ? PrescriptionStatus.EXPIRED : status;
    }

    /** Zmiana stanu zainicjowana przez e-receipt (poprawnosc przejscia sprawdza wywolujacy serwis). */
    public void applyExternalStatus(PrescriptionStatus target, Instant at, String reason) {
        this.status = target;
        if (target == PrescriptionStatus.CANCELLED) {
            this.cancelledAt = at;
            this.cancelReason = reason;
        }
    }

    /** Anulowanie (poprawnosc przejscia sprawdza serwis). */
    void cancel(Instant at, String reason) {
        this.status = PrescriptionStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancelReason = reason;
    }
}
