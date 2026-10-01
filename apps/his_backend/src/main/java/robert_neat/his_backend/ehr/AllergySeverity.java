package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AllergySeverity` z kontraktu. */
public enum AllergySeverity implements WireEnum {
    MILD("mild"),
    MODERATE("moderate"),
    SEVERE("severe"),
    LIFE_THREATENING("life_threatening");

    private final String wire;

    AllergySeverity(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AllergySeverity> {

        public JpaConverter() {
            super(AllergySeverity.class);
        }
    }
}
