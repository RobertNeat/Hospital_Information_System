package robert_neat.his_backend.imaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `PregnancyStatus` z kontraktu (lista kontrolna bezpieczenstwa): nie / tak / nieznany / nie dotyczy. */
public enum PregnancyStatus implements WireEnum {
    NO("no"),
    YES("yes"),
    UNKNOWN("unknown"),
    NA("na");

    private final String wire;

    PregnancyStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<PregnancyStatus> {

        public JpaConverter() {
            super(PregnancyStatus.class);
        }
    }
}
