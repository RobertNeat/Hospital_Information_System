package robert_neat.his_backend.imaging.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.imaging.ImagingResultStatus;

/**
 * Zdarzenie domenowe: zapisano wynik badania obrazowego (publikowane w transakcji zapisu, bez konsumenta - alert
 * `critical_result` z celem `imaging_result` w kolejnych etapach). `critical` = flaga ustawiona przez radiologa.
 * `orderedById` = zlecajacy (adresat alertu) albo `null` dla wyniku bez zlecenia; `actorId` = rejestrujacy albo
 * `null` (aktor systemowy).
 */
public record ImagingResultRecorded(
        UUID resultId,
        UUID patientId,
        UUID orderId,
        UUID orderedById,
        ImagingModality modality,
        ImagingResultStatus status,
        boolean critical,
        Instant recordedAt,
        UUID actorId) {
}
