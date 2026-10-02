package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.patient.PatientSummaryResponse;

/** `ResultWithPatient<ImagingResult>` z kontraktu: pola wyniku (splaszczone) + `patient` (`PatientSummary`). */
public record ImagingResultWithPatientResponse(
        UUID id,
        UUID patientId,
        UUID orderId,
        ImagingModality modality,
        String examName,
        String bodyRegion,
        Instant performedAt,
        Instant reportedAt,
        String radiologistName,
        UUID radiologistId,
        String technique,
        String findings,
        String conclusion,
        ImagingResultStatus status,
        int imageCount,
        boolean critical,
        Instant reviewedAt,
        UUID reviewedById,
        long version,
        PatientSummaryResponse patient) {

    static ImagingResultWithPatientResponse of(ImagingResultResponse r, PatientSummaryResponse patient) {
        return new ImagingResultWithPatientResponse(r.id(), r.patientId(), r.orderId(), r.modality(), r.examName(),
                r.bodyRegion(), r.performedAt(), r.reportedAt(), r.radiologistName(), r.radiologistId(),
                r.technique(), r.findings(), r.conclusion(), r.status(), r.imageCount(), r.critical(),
                r.reviewedAt(), r.reviewedById(), r.version(), patient);
    }
}
