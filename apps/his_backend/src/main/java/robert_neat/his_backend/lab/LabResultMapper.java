package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). Liczby w JSON
 * bez zbednych zer i bez notacji wykladniczej (`4520`, `7.2`).
 */
final class LabResultMapper {

    private LabResultMapper() {
    }

    /** `LabResultCreateRequest` (REST) -> {@link RecordLabResultCommand}; `orderId` i `performerName` z kontekstu wywolania. */
    static RecordLabResultCommand toCommand(LabResultCreateRequest r, UUID patientId, UUID orderId) {
        List<RecordLabResultCommand.ObservationInput> observations = r.observations() == null ? List.of()
                : r.observations().stream()
                        .map(o -> new RecordLabResultCommand.ObservationInput(o.analyteCode(), o.numericValue(),
                                o.textValue(), o.flag()))
                        .toList();
        return new RecordLabResultCommand(patientId, orderId, r.orderItemId(), r.testCode(), r.collectedAt(),
                r.resultedAt(), r.status(), null, r.comment(), observations);
    }

    static LabResultResponse toResponse(LabResult r) {
        return new LabResultResponse(r.getId(), r.getPatientId(), r.getOrderId(), r.getOrderItemId(),
                r.getTestCode(), r.getTestName(), r.getCategory(), r.getCollectedAt(), r.getResultedAt(),
                r.getStatus(), r.getObservations().stream().map(LabResultMapper::toResponse).toList(),
                r.getPerformerName(), r.getComment(), r.getReviewedAt(), r.getReviewedById(), r.getVersion());
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
