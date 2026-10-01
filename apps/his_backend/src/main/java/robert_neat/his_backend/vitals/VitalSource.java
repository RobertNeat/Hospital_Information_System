package robert_neat.his_backend.vitals;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `VitalsSource` z kontraktu: pochodzenie odczytu (brak w zadaniu = `manual`). */
public enum VitalSource implements WireEnum {
    MANUAL("manual"),
    MONITOR("monitor");

    private final String wire;

    VitalSource(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<VitalSource> {

        public JpaConverter() {
            super(VitalSource.class);
        }
    }
}
