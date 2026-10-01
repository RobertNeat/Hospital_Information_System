package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `VitalType` z kontraktu (rodzaj parametru zyciowego); kolejnosc deklaracji = kolejnosc progow w odpowiedzi. */
public enum VitalType implements WireEnum {
    SYSTOLIC("systolic"),
    DIASTOLIC("diastolic"),
    HEART_RATE("heartRate"),
    TEMPERATURE("temperature"),
    SPO2("spo2"),
    RESPIRATORY_RATE("respiratoryRate");

    private final String wire;

    VitalType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<VitalType> {

        public JpaConverter() {
            super(VitalType.class);
        }
    }
}
