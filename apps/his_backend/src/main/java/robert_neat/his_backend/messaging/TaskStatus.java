package robert_neat.his_backend.messaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `TaskStatus` z kontraktu; dozwolone przejscia: {@link TeamTaskStateMachine}. */
public enum TaskStatus implements WireEnum {
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    DONE("done"),
    CANCELLED("cancelled");

    private final String wire;

    TaskStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<TaskStatus> {

        public JpaConverter() {
            super(TaskStatus.class);
        }
    }
}
