package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `LabCategory` z kontraktu (kategoria badania laboratoryjnego). */
public enum LabCategory implements WireEnum {
    HEMATOLOGY("hematology"),
    BIOCHEMISTRY("biochemistry"),
    COAGULATION("coagulation"),
    IMMUNOLOGY("immunology"),
    URINALYSIS("urinalysis"),
    MICROBIOLOGY("microbiology"),
    PATHOLOGY("pathology");

    private final String wire;

    LabCategory(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<LabCategory> {

        public JpaConverter() {
            super(LabCategory.class);
        }
    }
}
