package robert_neat.his_backend.patient.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.patient.DischargeDisposition;

/** Zdarzenie domenowe: pacjent wypisany (publikowane w transakcji wypisu; konsumenci w kolejnych etapach). */
public record PatientDischarged(
        UUID patientId,
        UUID admissionId,
        UUID encounterId,
        UUID wardId,
        Instant dischargedAt,
        DischargeDisposition disposition,
        UUID dischargeSummaryNoteId,
        UUID actorId) {
}
