package robert_neat.his_backend.imaging;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `Laterality` z kontraktu: strona badania (`na` - nie dotyczy). */
public enum Laterality implements WireEnum {
    LEFT("left"),
    RIGHT("right"),
    BILATERAL("bilateral"),
    NA("na");

    private final String wire;

    Laterality(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<Laterality> {

        public JpaConverter() {
            super(Laterality.class);
        }
    }
}
