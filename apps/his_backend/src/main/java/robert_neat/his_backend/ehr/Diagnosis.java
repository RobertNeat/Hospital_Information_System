package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.persistence.VersionedEntity;

/** Diagnoza (`diagnosis`); kod jako osadzone {@link Coding}. */
@Entity
@Table(name = "diagnosis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diagnosis extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Embedded
    private Coding code;

    @Column(name = "type", nullable = false, length = 10)
    private DiagnosisType type;

    @Column(name = "status", nullable = false, length = 10)
    private DiagnosisStatus status;

    @Column(name = "diagnosed_at", nullable = false)
    private Instant diagnosedAt;

    @Column(name = "diagnosed_by_id", nullable = false)
    private UUID diagnosedById;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public static Diagnosis record(UUID patientId, UUID encounterId, Coding code, DiagnosisType type,
            DiagnosisStatus status, Instant diagnosedAt, UUID diagnosedById, String notes) {
        Diagnosis d = new Diagnosis();
        d.patientId = patientId;
        d.encounterId = encounterId;
        d.code = code;
        d.type = type;
        d.status = status;
        d.diagnosedAt = diagnosedAt;
        d.diagnosedById = diagnosedById;
        d.notes = notes;
        return d;
    }
}
