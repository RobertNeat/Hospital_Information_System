package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kontakt pacjenta ze szpitalem. Tabela ma tylko znaczniki czasu (bez autora i `version`), wiec encja nie dziedziczy
 * po {@code AuditableEntity}. Przyjecie tworzy Encounter, wypis go zamyka; pelny EHR (K9) rozszerza ta encje.
 */
@Entity
@Table(name = "encounter")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "type", nullable = false, length = 20)
    private EncounterType type;

    @Column(name = "status", nullable = false, length = 15)
    private EncounterStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "practitioner_id")
    private UUID practitionerId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "episode_id")
    private UUID episodeId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Encounter start(UUID patientId, EncounterType type, Instant startAt, UUID wardId,
            UUID practitionerId, String reason) {
        Encounter e = new Encounter();
        e.patientId = patientId;
        e.type = type;
        e.status = EncounterStatus.IN_PROGRESS;
        e.startAt = startAt;
        e.wardId = wardId;
        e.practitionerId = practitionerId;
        e.reason = reason;
        return e;
    }

    /** Zamyka kontakt (`finished`); `endAt` nie moze byc wczesniejszy niz `startAt`. Zakonczony/anulowany bez zmian. */
    public void finish(Instant at) {
        if (status == EncounterStatus.FINISHED || status == EncounterStatus.CANCELLED) {
            return;
        }
        this.status = EncounterStatus.FINISHED;
        this.endAt = at.isBefore(startAt) ? startAt : at;
    }
}
