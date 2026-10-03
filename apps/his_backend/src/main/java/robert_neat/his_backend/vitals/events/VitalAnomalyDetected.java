package robert_neat.his_backend.vitals.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.vitals.VitalAnomaly;

/**
 * Zdarzenie domenowe: zapisany odczyt ma co najmniej jedna anomalie (`warning` lub `critical`), publikowane w
 * transakcji zapisu; konsument: `alert/AlertEventListener` - synchronicznie w transakcji zrodlowej, alert
 * `vital_anomaly` z severity wg najwyzszej anomalii w zdarzeniu. `anomalies` zawiera wszystkie anomalie zapisu
 * (nie tylko krytyczne); `actorId` = rejestrujacy.
 */
public record VitalAnomalyDetected(
        UUID patientId,
        UUID vitalsId,
        List<VitalAnomaly> anomalies,
        Instant recordedAt,
        UUID actorId) {
}
