package robert_neat.elaboratory.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fabryki obiektow domeny dla testow z innych pakietow (konstruktory sa wewnetrzne dla pakietu `order`). */
public final class OrderFixtures {

    private OrderFixtures() {
    }

    public static LabOrder order(LabOrderDraft draft) {
        return new LabOrder(draft, Instant.now());
    }

    public static ResultEntry entry(String testCode, ResultStatus status, Observation... observations) {
        Instant now = Instant.now();
        return new ResultEntry(UUID.randomUUID().toString(), testCode, status, "Laborant", null, now.minusSeconds(60),
                now, List.of(observations));
    }
}
