package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;

/**
 * Wewnetrzne polecenie zapisu wyniku badania obrazowego (budowane z `DiagnosticReport` z `e-imaging` albo z
 * `POST /api/v1/imaging-orders/{orderId}/results`). Wynik zewnetrzny (`orderId` = null) wymaga `modality`, `examName`
 * i `bodyRegion`; dla wyniku zlecenia
 * brakujace pola snapshotu pochodza ze zlecenia (podane musza byc zgodne - modalnosc). `radiologistName` pusty = nazwa
 * pracownika (`radiologistId`, a gdy brak - zalogowanego); `radiologistId` pusty przy podanej nazwie = bez FK.
 * `imageCount` pusty = 0. `critical` ustawia radiolog (nie jest wyliczane z opisu).
 */
public record RecordImagingResultCommand(
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
        Integer imageCount,
        boolean critical) {
}
