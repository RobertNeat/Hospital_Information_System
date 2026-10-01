package robert_neat.his_backend.vitals.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.vitals.VitalAnomaly;

/**
 * Zdarzenie domenowe: zapisany odczyt ma anomalie `critical` (publikowane w transakcji zapisu, tylko gdy jest co
 * najmniej jedna anomalia krytyczna; konsument: `alert/AlertEventListener` - synchronicznie w transakcji zrodlowej, alert `vital_anomaly`). `anomalies` zawiera
 * wylacznie anomalie krytyczne; `actorId` = rejestrujacy.
 */
public record VitalAnomalyDetected(
        UUID patientId,
        UUID vitalsId,
        List<VitalAnomaly> anomalies,
        Instant recordedAt,
        UUID actorId) {
}
