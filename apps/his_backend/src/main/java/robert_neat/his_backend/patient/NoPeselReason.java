package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `NoPeselReason` z kontraktu. */
public enum NoPeselReason implements WireEnum {
    FOREIGNER("foreigner"),
    NEWBORN("newborn"),
    UNKNOWN_IDENTITY("unknown_identity");

    private final String wire;

    NoPeselReason(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<NoPeselReason> {

        public JpaConverter() {
            super(NoPeselReason.class);
        }
    }
}
