package robert_neat.his_backend.prescription;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `PrescriptionKind` z kontraktu. */
public enum PrescriptionKind implements WireEnum {
    E_PRESCRIPTION("e_prescription"),
    HOSPITAL_ORDER("hospital_order");

    private final String wire;

    PrescriptionKind(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<PrescriptionKind> {

        public JpaConverter() {
            super(PrescriptionKind.class);
        }
    }
}
