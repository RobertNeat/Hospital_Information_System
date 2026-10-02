package robert_neat.his_backend.imaging;

import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;

/** Reczne mapowanie encja -> DTO. */
final class ImagingResultMapper {

    private ImagingResultMapper() {
    }

    /**
     * `ImagingResultCreateRequest` (REST) -> {@link RecordImagingResultCommand}; `orderId` ze sciezki, `modality` z
     * polecenia jest pomijana przez {@link ImagingResultRecordingService} na rzecz zlecenia (gdy `orderId` podane);
     * radiolog ustalany z sesji przez serwis (`radiologistName`/`radiologistId` = `null`).
     */
    static RecordImagingResultCommand toCommand(ImagingResultCreateRequest r, UUID patientId, UUID orderId,
            ImagingModality modality) {
        return new RecordImagingResultCommand(patientId, orderId, modality, null, null, r.performedAt(),
                r.reportedAt(), null, null, r.technique(), r.findings(), r.conclusion(), r.status(), r.imageCount(),
                r.critical());
    }

    static ImagingResultResponse toResponse(ImagingResult r) {
        return new ImagingResultResponse(r.getId(), r.getPatientId(), r.getOrderId(), r.getModality(),
                r.getExamName(), r.getBodyRegion(), r.getPerformedAt(), r.getReportedAt(), r.getRadiologistName(),
                r.getRadiologistId(), r.getTechnique(), r.getFindings(), r.getConclusion(), r.getStatus(),
                r.getImageCount(), r.isCritical(), r.getReviewedAt(), r.getReviewedById(), r.getVersion());
    }
}
