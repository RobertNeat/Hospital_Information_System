package robert_neat.his_backend.lab.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.lab.ResultStatus;

/**
 * Zdarzenie domenowe: zapisano wynik laboratoryjny (publikowane w transakcji zapisu, bez konsumenta - alert
 * `critical_result` w kolejnych etapach). `critical` = jakakolwiek obserwacja z flaga LL/HH (kody w
 * `criticalAnalyteCodes`). `orderedById` = zlecajacy (adresat alertu) albo `null` dla wyniku bez zlecenia;
 * `actorId` = rejestrujacy albo `null` (aktor systemowy).
 */
public record LabResultRecorded(
        UUID resultId,
        UUID patientId,
        UUID orderId,
        UUID orderedById,
        String testCode,
        ResultStatus status,
        boolean critical,
        List<String> criticalAnalyteCodes,
        Instant recordedAt,
        UUID actorId) {
}
