package robert_neat.his_backend.imaging;

/** Reczne mapowanie encja -> DTO. */
final class ImagingResultMapper {

    private ImagingResultMapper() {
    }

    static ImagingResultResponse toResponse(ImagingResult r) {
        return new ImagingResultResponse(r.getId(), r.getPatientId(), r.getOrderId(), r.getModality(),
                r.getExamName(), r.getBodyRegion(), r.getPerformedAt(), r.getReportedAt(), r.getRadiologistName(),
                r.getRadiologistId(), r.getTechnique(), r.getFindings(), r.getConclusion(), r.getStatus(),
                r.getImageCount(), r.isCritical(), r.getReviewedAt(), r.getReviewedById());
    }
}
