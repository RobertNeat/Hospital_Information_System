package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `InsuranceStatus` z kontraktu. */
public enum InsuranceStatus implements WireEnum {
    ACTIVE("active"),
    INACTIVE("inactive"),
    UNKNOWN("unknown");

    private final String wire;

    InsuranceStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<InsuranceStatus> {

        public JpaConverter() {
            super(InsuranceStatus.class);
        }
    }
}
