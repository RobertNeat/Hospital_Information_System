package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;

/**
 * ImagingResult z kontraktu (pola opcjonalne - `orderId`, `radiologistId`, `technique`, `reviewed*` - pomijane, gdy
 * brak).
 */
public record ImagingResultResponse(
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
        long version) {
}
