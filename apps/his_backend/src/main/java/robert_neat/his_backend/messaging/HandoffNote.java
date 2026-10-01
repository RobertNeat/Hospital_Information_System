package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Przekazanie zmiany (`handoff_note`) z notatkami SBAR per pacjent; niezmienne po utworzeniu. */
@Entity
@Table(name = "handoff_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoffNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ward_id", nullable = false, updatable = false)
    private UUID wardId;

    @Column(name = "shift_date", nullable = false, updatable = false)
    private LocalDate shiftDate;

    @Column(name = "shift", nullable = false, updatable = false, length = 10)
    private ShiftType shift;

    @Column(name = "from_id", nullable = false, updatable = false)
    private UUID fromId;

    @Column(name = "to_id", nullable = false, updatable = false)
    private UUID toId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "general_notes", columnDefinition = "text")
    private String generalNotes;

    @ElementCollection
    @CollectionTable(name = "handoff_patient_note", joinColumns = @JoinColumn(name = "handoff_id"))
    @OrderBy("patientId ASC")
    @BatchSize(size = 100)
    private List<HandoffPatientNote> patientNotes = new ArrayList<>();

    static HandoffNote create(UUID wardId, LocalDate shiftDate, ShiftType shift, UUID fromId, UUID toId,
            Instant createdAt, String generalNotes, List<HandoffPatientNote> patientNotes) {
        HandoffNote n = new HandoffNote();
        n.wardId = wardId;
        n.shiftDate = shiftDate;
        n.shift = shift;
        n.fromId = fromId;
        n.toId = toId;
        n.createdAt = createdAt;
        n.generalNotes = generalNotes;
        n.patientNotes.addAll(patientNotes);
        return n;
    }
}
