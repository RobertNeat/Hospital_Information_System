package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AllergyCategory` z kontraktu. */
public enum AllergyCategory implements WireEnum {
    DRUG("drug"),
    FOOD("food"),
    ENVIRONMENT("environment"),
    OTHER("other");

    private final String wire;

    AllergyCategory(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AllergyCategory> {

        public JpaConverter() {
            super(AllergyCategory.class);
        }
    }
}
