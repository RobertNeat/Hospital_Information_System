package robert_neat.elaboratory.order;

import java.time.Instant;
import java.util.List;

/** Zlecenie przyjete z HIS (`id` zlecenia HIS jest tez identyfikatorem w e-laboratory). */
public record LabOrderDraft(
        String hisOrderId,
        String patientRef,
        String requesterRef,
        String priority,
        Instant authoredOn,
        Instant plannedCollectionAt,
        boolean fasting,
        List<String> notes,
        List<LabItem> items) {

    public LabOrderDraft {
        notes = List.copyOf(notes);
        items = List.copyOf(items);
    }
}
