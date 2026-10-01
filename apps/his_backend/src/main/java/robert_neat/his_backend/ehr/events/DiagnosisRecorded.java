package robert_neat.his_backend.ehr.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.ehr.Coding;
import robert_neat.his_backend.ehr.DiagnosisType;

/** Zdarzenie domenowe: zapisano diagnoze (publikowane w transakcji; konsumenci w kolejnych etapach). */
public record DiagnosisRecorded(
        UUID diagnosisId,
        UUID patientId,
        UUID encounterId,
        Coding code,
        DiagnosisType type,
        Instant diagnosedAt,
        UUID actorId) {
}
