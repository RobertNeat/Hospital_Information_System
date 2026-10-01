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

/** Przeciwwskazanie (`contraindication`); tylko odczyt w kontrakcie, tabela bez audytu i `version`. */
@Entity
@Table(name = "contraindication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contraindication {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
