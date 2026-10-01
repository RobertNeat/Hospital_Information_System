package robert_neat.his_backend.vitals;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `VitalsContext` z kontraktu: okolicznosci pomiaru. */
public enum VitalContext implements WireEnum {
    OFFICE_EXAM("office_exam"),
    WARD_ROUND("ward_round"),
    TRIAGE("triage"),
    OBSERVATION("observation");

    private final String wire;

    VitalContext(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<VitalContext> {

        public JpaConverter() {
            super(VitalContext.class);
        }
    }
}
