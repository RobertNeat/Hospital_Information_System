package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Epizod leczenia (`treatment_episode`), tylko odczyt. Relacja M:N z diagnozami (`episode_diagnosis`, wlascicielem jest
 * epizod) mapowana jako zbior identyfikatorow - dzieki temu modul `patient` nie zalezy od modulu `ehr`.
 * {@link Encounter} wskazuje epizod skalarnie (`episodeId`).
 */
@Entity
@Table(name = "treatment_episode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TreatmentEpisode {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "status", nullable = false, length = 10)
    private EpisodeStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "episode_diagnosis", joinColumns = @JoinColumn(name = "episode_id"))
    @Column(name = "diagnosis_id", nullable = false)
    @BatchSize(size = 100)
    private Set<UUID> diagnosisIds = new HashSet<>();
}
