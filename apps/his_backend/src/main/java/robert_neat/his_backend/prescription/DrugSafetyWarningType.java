package robert_neat.his_backend.prescription;

import robert_neat.his_backend.common.wire.WireEnum;

/** `DrugSafetyWarningType` z kontraktu. */
public enum DrugSafetyWarningType implements WireEnum {
    ALLERGY("allergy"),
    INTERACTION("interaction"),
    DUPLICATE("duplicate"),
    MAX_DOSE("max_dose");

    private final String wire;

    DrugSafetyWarningType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
