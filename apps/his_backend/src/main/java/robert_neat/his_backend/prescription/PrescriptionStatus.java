package robert_neat.his_backend.prescription;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `PrescriptionStatus` z kontraktu. Stany koncowe (brak przejsc): dispensed, cancelled, expired. */
public enum PrescriptionStatus implements WireEnum {
    ISSUED("issued"),
    PARTIALLY_DISPENSED("partially_dispensed"),
    DISPENSED("dispensed"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String wire;

    PrescriptionStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    /** `issued` i `partially_dispensed` - recepta nadal "zywa" (moze wygasnac albo zostac anulowana). */
    public boolean isOpen() {
        return this == ISSUED || this == PARTIALLY_DISPENSED;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<PrescriptionStatus> {

        public JpaConverter() {
            super(PrescriptionStatus.class);
        }
    }
}
