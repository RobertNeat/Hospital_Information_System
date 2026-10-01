package robert_neat.his_backend.prescription;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `DoseFrequency` z kontraktu (QD = 1x dziennie ... QW = 1x w tygodniu, PRN = doraznie). */
public enum DoseFrequency implements WireEnum {
    QD("QD"),
    BID("BID"),
    TID("TID"),
    QID("QID"),
    Q4H("Q4H"),
    Q6H("Q6H"),
    Q8H("Q8H"),
    Q12H("Q12H"),
    QW("QW"),
    PRN("PRN");

    private final String wire;

    DoseFrequency(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<DoseFrequency> {

        public JpaConverter() {
            super(DoseFrequency.class);
        }
    }
}
