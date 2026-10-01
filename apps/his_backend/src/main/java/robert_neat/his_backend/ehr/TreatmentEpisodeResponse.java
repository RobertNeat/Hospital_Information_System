package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.patient.EpisodeStatus;

/** TreatmentEpisode z kontraktu; diagnosisIds zawsze obecne (puste = brak diagnoz). */
public record TreatmentEpisodeResponse(
        UUID id,
        UUID patientId,
        String title,
        Instant startAt,
        Instant endAt,
        EpisodeStatus status,
        List<UUID> diagnosisIds) {
}
