package robert_neat.eimaging.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fabryki obiektow domeny dla testow z innych pakietow (konstruktory sa wewnetrzne dla pakietu `order`). */
public final class OrderFixtures {

    private OrderFixtures() {
    }

    public static ImagingOrderDraft draft(String id, ImagingOrderStatus status) {
        return new ImagingOrderDraft(id, "Patient/0f0db024-a3c0-56a5-916d-3acf34a052e5", "Practitioner/s", "routine",
                Instant.now(), null, null, "RTG-KOL", "RTG stawu kolanowego", "RTG", "right", "Staw kolanowy", false,
                "Bol po urazie.", List.of(), status);
    }

    public static ImagingOrder order(ImagingOrderDraft draft) {
        return new ImagingOrder(draft, Instant.now());
    }

    public static ResultEntry entry(ResultStatus status, boolean critical) {
        Instant now = Instant.now();
        return new ResultEntry(UUID.randomUUID().toString(), status, "Radiolog", "Opis badania.", "Wniosek.",
                critical, now.minusSeconds(60), now);
    }
}
