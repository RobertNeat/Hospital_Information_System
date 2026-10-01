package robert_neat.ereceipt.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Recepta przyjeta z HIS, jeszcze bez klucza `eRxKey` (nadaje go {@link ReceiptService}). */
public record ReceiptDraft(
        String hisPrescriptionId,
        String accessCode,
        String patientRef,
        String requesterRef,
        Instant authoredOn,
        LocalDate validFrom,
        LocalDate validUntil,
        String medication,
        List<String> dosages,
        String note) {
}
