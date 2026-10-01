package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `DischargeDisposition` z kontraktu. */
public enum DischargeDisposition implements WireEnum {
    HOME("home"),
    TRANSFER("transfer"),
    DECEASED("deceased"),
    AGAINST_ADVICE("against_advice"),
    OTHER("other");

    private final String wire;

    DischargeDisposition(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<DischargeDisposition> {

        public JpaConverter() {
            super(DischargeDisposition.class);
        }
    }
}
