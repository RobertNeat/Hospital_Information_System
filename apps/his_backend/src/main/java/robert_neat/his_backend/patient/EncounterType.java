package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `EncounterType` z kontraktu. */
public enum EncounterType implements WireEnum {
    VISIT("visit"),
    CONSULTATION("consultation"),
    HOSPITALIZATION("hospitalization"),
    EMERGENCY("emergency"),
    TELECONSULTATION("teleconsultation");

    private final String wire;

    EncounterType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<EncounterType> {

        public JpaConverter() {
            super(EncounterType.class);
        }
    }
}
