package robert_neat.his_backend.patient.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.patient.AdmissionType;

/** Zdarzenie domenowe: pacjent przyjety (publikowane w transakcji przyjecia; konsument: `alert/AlertEventListener`, alert `system`). */
public record PatientAdmitted(
        UUID patientId,
        UUID admissionId,
        UUID encounterId,
        UUID wardId,
        UUID attendingPhysicianId,
        AdmissionType admissionType,
        Instant admittedAt,
        UUID actorId) {
}
