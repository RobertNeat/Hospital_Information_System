package robert_neat.eimaging.order;

import java.time.Instant;
import java.util.List;

/**
 * Zlecenie przyjete z HIS (`id` zlecenia HIS jest tez identyfikatorem w e-imaging). `status` to stan zlecenia w HIS w
 * chwili wysylki (zlecenie ze slotem jest od razu `scheduled`).
 */
public record ImagingOrderDraft(
        String hisOrderId,
        String patientRef,
        String requesterRef,
        String priority,
        Instant authoredOn,
        Instant scheduledAt,
        String slotId,
        String examCode,
        String examName,
        String modality,
        String laterality,
        String bodyRegion,
        boolean contrast,
        String indication,
        List<String> notes,
        ImagingOrderStatus status) {

    public ImagingOrderDraft {
        notes = List.copyOf(notes);
    }
}
