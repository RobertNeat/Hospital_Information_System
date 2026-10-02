package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.catalog.LabCategory;

/**
 * LabResult z kontraktu (pola opcjonalne - `orderId`, `orderItemId`, `comment`, `reviewed*` - pomijane, gdy brak).
 * Obserwacje posortowane po kodzie analitu (schemat nie przechowuje kolejnosci).
 */
public record LabResultResponse(
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
        List<Observation> observations,
        String performerName,
        String comment,
        Instant reviewedAt,
        UUID reviewedById,
        long version) {

    /** `LabObservation`; `value` to liczba (`BigDecimal`) albo string - zaleznie od wypelnionej kolumny. */
    public record Observation(
            String analyteCode,
            String analyteName,
            Object value,
            String unit,
            Range referenceRange,
            ObservationFlag flag) {
    }

    /** `ReferenceRange`; zawsze obecny (pusty obiekt, gdy brak zakresu). */
    public record Range(BigDecimal low, BigDecimal high, String text) {
    }
}
