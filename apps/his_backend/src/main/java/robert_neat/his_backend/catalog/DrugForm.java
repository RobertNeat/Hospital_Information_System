package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `DrugForm` z kontraktu (postac leku). */
public enum DrugForm implements WireEnum {
    TABLET("tablet"),
    CAPSULE("capsule"),
    INJECTION("injection"),
    SYRUP("syrup"),
    DROPS("drops"),
    OINTMENT("ointment"),
    INHALER("inhaler"),
    SUPPOSITORY("suppository"),
    PATCH("patch");

    private final String wire;

    DrugForm(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<DrugForm> {

        public JpaConverter() {
            super(DrugForm.class);
        }
    }
}
