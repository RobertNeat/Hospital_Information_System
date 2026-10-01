package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AdministrationRoute` z kontraktu (droga podania). */
public enum AdministrationRoute implements WireEnum {
    ORAL("oral"),
    SUBLINGUAL("sublingual"),
    IV("iv"),
    IM("im"),
    SC("sc"),
    TOPICAL("topical"),
    INHALATION("inhalation"),
    RECTAL("rectal"),
    TRANSDERMAL("transdermal");

    private final String wire;

    AdministrationRoute(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AdministrationRoute> {

        public JpaConverter() {
            super(AdministrationRoute.class);
        }
    }
}
