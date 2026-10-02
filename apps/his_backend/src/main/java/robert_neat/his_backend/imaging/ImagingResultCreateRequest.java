package robert_neat.his_backend.imaging;

import java.time.Instant;

/**
 * Zadanie `POST /api/v1/imaging-orders/{orderId}/results` (radiolog, `imaging-result:write`). Odpowiednik natywny
 * `POST /fhir/DiagnosticReport`: mapowane na to samo {@link RecordImagingResultCommand} i
 * {@link ImagingResultRecordingService#recordResult}. `modality`/`examName`/`bodyRegion` pochodza ze zlecenia
 * (sciezka zawsze ma `orderId`); `radiologistName`/`radiologistId` z aktora sesji (nie mozna ich podac w zadaniu).
 */
public record ImagingResultCreateRequest(
        Instant performedAt,
        Instant reportedAt,
        String technique,
        String findings,
        String conclusion,
        ImagingResultStatus status,
        Integer imageCount,
        boolean critical) {
}
