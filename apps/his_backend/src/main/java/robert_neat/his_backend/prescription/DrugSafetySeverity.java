package robert_neat.his_backend.prescription;

import robert_neat.his_backend.common.wire.WireEnum;

/** `DrugSafetySeverity` z kontraktu. */
public enum DrugSafetySeverity implements WireEnum {
    WARN("warn"),
    DANGER("danger");

    private final String wire;

    DrugSafetySeverity(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
