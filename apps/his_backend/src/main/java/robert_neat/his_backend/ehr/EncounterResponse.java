package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.patient.EncounterStatus;
import robert_neat.his_backend.patient.EncounterType;

/** Encounter z kontraktu (bez audytu - tabela ma tylko wewnetrzne znaczniki czasu). */
public record EncounterResponse(
        UUID id,
        UUID patientId,
        EncounterType type,
        EncounterStatus status,
        Instant startAt,
        Instant endAt,
        UUID wardId,
        UUID practitionerId,
        String reason,
        String summary,
        UUID episodeId) {
}
