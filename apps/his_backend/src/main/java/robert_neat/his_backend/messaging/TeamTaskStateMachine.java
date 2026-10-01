package robert_neat.his_backend.messaging;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Dozwolone przejscia statusu zadania. `done` i `cancelled` sa koncowe; `cancelled` z kazdego stanu niekoncowego.
 * `open -> done` jest dozwolone (UI ma akcje "Zakoncz" takze dla zadan nierozpoczetych).
 */
final class TeamTaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        TRANSITIONS.put(TaskStatus.OPEN, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED));
        TRANSITIONS.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.DONE, TaskStatus.CANCELLED));
        TRANSITIONS.put(TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class));
        TRANSITIONS.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
    }

    private TeamTaskStateMachine() {
    }

    static boolean canTransition(TaskStatus from, TaskStatus to) {
        return TRANSITIONS.get(from).contains(to);
    }
}
