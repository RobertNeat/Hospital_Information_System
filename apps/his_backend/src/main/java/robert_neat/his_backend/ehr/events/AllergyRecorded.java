package robert_neat.his_backend.ehr.events;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import robert_neat.his_backend.ehr.AllergySeverity;

/** Zdarzenie domenowe: zapisano alergie (publikowane w transakcji; konsument: `alert/AlertEventListener`, alert `system`/`warning`). */
public record AllergyRecorded(
        UUID allergyId,
        UUID patientId,
        String substance,
        AllergySeverity severity,
        Set<String> atcCodes,
        Instant recordedAt,
        UUID actorId) {
}
