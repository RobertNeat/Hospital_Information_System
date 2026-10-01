package robert_neat.his_backend.vitals;

import java.util.List;

import robert_neat.his_backend.patient.PatientSummaryResponse;

/**
 * `WardVitalsRow` z kontraktu (przeglad oddzialu). `latest` brak, gdy pacjent nie ma odczytow; `lastMeasuredAgoMin`
 * (`@viewerScoped`) = minuty od `latest.recordedAt` do "teraz".
 */
public record WardVitalsRow(
        PatientSummaryResponse patient,
        VitalSignsResponse latest,
        List<VitalAnomaly> anomalies,
        Long lastMeasuredAgoMin) {
}
