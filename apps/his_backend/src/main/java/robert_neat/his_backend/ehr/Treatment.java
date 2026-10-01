package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Leczenie (`treatment`); tylko odczyt w kontrakcie, tabela bez audytu i `version`. */
@Entity
@Table(name = "treatment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Treatment {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    private TreatmentType type;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "status", nullable = false, length = 15)
    private TreatmentStatus status;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;
}
