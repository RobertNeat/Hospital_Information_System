package robert_neat.his_backend.vitals;

import robert_neat.his_backend.common.wire.WireEnum;

/** `AnomalySeverity` z kontraktu: `warning` = poza norma (low/high), `critical` = poza progami krytycznymi. */
public enum AnomalySeverity implements WireEnum {
    WARNING("warning"),
    CRITICAL("critical");

    private final String wire;

    AnomalySeverity(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
