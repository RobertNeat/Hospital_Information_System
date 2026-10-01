package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

/** `Admission` z kontraktu (models/patient.model.ts). Pola opcjonalne (null) sa pomijane (NON_ABSENT). */
public record AdmissionResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        AdmissionRecordStatus status,
        AdmissionType admissionType,
        Instant admittedAt,
        UUID wardId,
        String room,
        String bed,
        UUID attendingPhysicianId,
        TriageLevel triageLevel,
        String reason,
        String referralNumber,
        Instant dischargedAt,
        DischargeDisposition dischargeDisposition,
        UUID dischargeSummaryNoteId,
        long version) {
}
