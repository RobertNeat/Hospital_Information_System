package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/**
 * `CodingSystem` z kontraktu (`ICD-10`, `ICD-9-PL`, `local` itd. na drucie). `SNOMED` to jedyny system faktycznie
 * zapisywany przez legalne przeplywy UI od wprowadzenia podpowiedzi terminologii SNOMED CT (zob. {@link Coding});
 * pozostale wartosci zostaja w enumie dla odczytu starszych danych i wyniku {@code SnowstormClient#translateToIcd10}.
 */
public enum CodingSystem implements WireEnum {
    ICD_10("ICD-10"),
    LOINC("LOINC"),
    ATC("ATC"),
    ICD_9_PL("ICD-9-PL"),
    LOCAL("local"),
    SNOMED("SNOMED");

    private final String wire;

    CodingSystem(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<CodingSystem> {

        public JpaConverter() {
            super(CodingSystem.class);
        }
    }
}
