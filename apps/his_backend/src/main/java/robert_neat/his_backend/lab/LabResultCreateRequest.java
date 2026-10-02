package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Zadanie `POST /api/v1/lab-orders/{orderId}/results` (laborant, `lab-result:write`). Odpowiednik natywny
 * `POST /fhir/DiagnosticReport`: mapowane na to samo {@link RecordLabResultCommand} i
 * {@link LabResultRecordingService#recordResult}. `orderItemId` lub `testCode` wskazuje pozycje zlecenia (jedna z
 * dwoch, zgodnie z {@link RecordLabResultCommand}); wykonawca to zawsze zalogowany laborant (brak pola
 * `performerName` - nie mozna go podac w zadaniu).
 */
public record LabResultCreateRequest(
        UUID orderItemId,
        String testCode,
        Instant collectedAt,
        Instant resultedAt,
        ResultStatus status,
        String comment,
        List<ObservationRequest> observations) {

    /** Dokladnie jedna z wartosci (`numericValue` albo `textValue`); `flag` opcjonalna. */
    public record ObservationRequest(String analyteCode, BigDecimal numericValue, String textValue,
            ObservationFlag flag) {
    }
}
