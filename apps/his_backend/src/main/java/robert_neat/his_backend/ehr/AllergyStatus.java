package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AllergyStatus` z kontraktu. */
public enum AllergyStatus implements WireEnum {
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String wire;

    AllergyStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AllergyStatus> {

        public JpaConverter() {
            super(AllergyStatus.class);
        }
    }
}
