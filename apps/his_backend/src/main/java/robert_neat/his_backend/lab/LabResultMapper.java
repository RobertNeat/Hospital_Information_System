package robert_neat.his_backend.lab;

import java.math.BigDecimal;

/**
 * Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). Liczby w JSON
 * bez zbednych zer i bez notacji wykladniczej (`4520`, `7.2`).
 */
final class LabResultMapper {

    private LabResultMapper() {
    }

    static LabResultResponse toResponse(LabResult r) {
        return new LabResultResponse(r.getId(), r.getPatientId(), r.getOrderId(), r.getOrderItemId(),
                r.getTestCode(), r.getTestName(), r.getCategory(), r.getCollectedAt(), r.getResultedAt(),
                r.getStatus(), r.getObservations().stream().map(LabResultMapper::toResponse).toList(),
                r.getPerformerName(), r.getComment(), r.getReviewedAt(), r.getReviewedById());
    }

    private static LabResultResponse.Observation toResponse(LabObservation o) {
        Object value = o.getValueNumeric() != null ? plain(o.getValueNumeric()) : o.getValueText();
        return new LabResultResponse.Observation(o.getAnalyteCode(), o.getAnalyteName(), value, o.getUnit(),
                toResponse(o.getReferenceRange()), o.getFlag());
    }

    private static LabResultResponse.Range toResponse(ReferenceRange range) {
        return range == null ? new LabResultResponse.Range(null, null, null)
                : new LabResultResponse.Range(plain(range.low()), plain(range.high()), range.text());
    }

    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
