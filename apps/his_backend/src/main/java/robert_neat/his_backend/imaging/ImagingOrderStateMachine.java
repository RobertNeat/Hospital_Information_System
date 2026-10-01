package robert_neat.his_backend.imaging;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import robert_neat.his_backend.common.order.OrderStatus;

/**
 * Dozwolone przejscia statusu zlecenia obrazowego (osobna maszyna niz laboratoryjna; `specimen_collected` nie
 * wystepuje). Dopuszczone jest pominiecie `in_progress` (`scheduled -> completed`, jak w danych mock). `completed` i
 * `cancelled` sa koncowe. Anulowanie jest dozwolone z kazdego stanu niekoncowego, ale wylacznie akcja `/cancel`.
 */
final class ImagingOrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.ORDERED,
                EnumSet.of(OrderStatus.SCHEDULED, OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.SCHEDULED,
                EnumSet.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.IN_PROGRESS, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.SPECIMEN_COLLECTED, EnumSet.noneOf(OrderStatus.class)); // nie dotyczy obrazowania
    }

    private ImagingOrderStateMachine() {
    }

    static boolean canTransition(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.get(from).contains(to);
    }

    /** Statusy, w ktorych zlecenie przyjmuje wynik badania. */
    static boolean acceptsResult(OrderStatus status) {
        return status == OrderStatus.SCHEDULED || status == OrderStatus.IN_PROGRESS;
    }
}
