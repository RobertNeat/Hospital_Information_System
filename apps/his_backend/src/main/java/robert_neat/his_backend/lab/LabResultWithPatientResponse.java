package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.catalog.LabCategory;
import robert_neat.his_backend.patient.PatientSummaryResponse;

/** `ResultWithPatient<LabResult>` z kontraktu: pola wyniku (splaszczone) + `patient` (`PatientSummary`). */
public record LabResultWithPatientResponse(
        UUID id,
        UUID patientId,
        UUID orderId,
        UUID orderItemId,
        String testCode,
        String testName,
        LabCategory category,
        Instant collectedAt,
        Instant resultedAt,
        ResultStatus status,
        List<LabResultResponse.Observation> observations,
        String performerName,
        String comment,
        Instant reviewedAt,
        UUID reviewedById,
        long version,
        PatientSummaryResponse patient) {

    static LabResultWithPatientResponse of(LabResultResponse r, PatientSummaryResponse patient) {
        return new LabResultWithPatientResponse(r.id(), r.patientId(), r.orderId(), r.orderItemId(), r.testCode(),
                r.testName(), r.category(), r.collectedAt(), r.resultedAt(), r.status(), r.observations(),
                r.performerName(), r.comment(), r.reviewedAt(), r.reviewedById(), r.version(), patient);
    }
}
