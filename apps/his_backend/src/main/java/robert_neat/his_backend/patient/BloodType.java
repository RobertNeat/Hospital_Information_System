package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `BloodType` z kontraktu; wartosci na drucie np. `0+`. */
public enum BloodType implements WireEnum {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    ZERO_POS("0+"),
    ZERO_NEG("0-");

    private final String wire;

    BloodType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<BloodType> {

        public JpaConverter() {
            super(BloodType.class);
        }
    }
}
