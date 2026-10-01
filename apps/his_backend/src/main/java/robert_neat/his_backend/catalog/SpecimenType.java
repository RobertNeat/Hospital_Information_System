package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `SpecimenType` z kontraktu (rodzaj materialu laboratoryjnego). */
public enum SpecimenType implements WireEnum {
    BLOOD("blood"),
    SERUM("serum"),
    URINE("urine"),
    STOOL("stool"),
    SWAB("swab"),
    CSF("csf"),
    TISSUE("tissue");

    private final String wire;

    SpecimenType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<SpecimenType> {

        public JpaConverter() {
            super(SpecimenType.class);
        }
    }
}
