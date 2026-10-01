package robert_neat.his_backend.lab;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import robert_neat.his_backend.common.order.OrderStatus;

/**
 * Dozwolone przejscia statusu zlecenia laboratoryjnego (wlacznie z pominieciem etapow pomocniczych, jak w danych
 * mock: `ordered -> specimen_collected`, `specimen_collected -> completed`). `completed` i `cancelled` sa koncowe.
 * Anulowanie jest dozwolone z kazdego stanu niekoncowego, ale wylacznie akcja `/cancel` (wymaga powodu).
 */
public final class LabOrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.ORDERED, EnumSet.of(OrderStatus.SCHEDULED, OrderStatus.SPECIMEN_COLLECTED,
                OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.SCHEDULED, EnumSet.of(OrderStatus.SPECIMEN_COLLECTED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.SPECIMEN_COLLECTED, EnumSet.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED,
                OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.IN_PROGRESS, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private LabOrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.get(from).contains(to);
    }
}
