package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `EncounterStatus` z kontraktu. */
public enum EncounterStatus implements WireEnum {
    PLANNED("planned"),
    IN_PROGRESS("in_progress"),
    FINISHED("finished"),
    CANCELLED("cancelled");

    private final String wire;

    EncounterStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<EncounterStatus> {

        public JpaConverter() {
            super(EncounterStatus.class);
        }
    }
}
