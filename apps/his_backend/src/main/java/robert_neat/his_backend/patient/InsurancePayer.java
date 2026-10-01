package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `InsurancePayer` z kontraktu. */
public enum InsurancePayer implements WireEnum {
    NFZ("NFZ"),
    PRIVATE("private"),
    NONE("none");

    private final String wire;

    InsurancePayer(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<InsurancePayer> {

        public JpaConverter() {
            super(InsurancePayer.class);
        }
    }
}
