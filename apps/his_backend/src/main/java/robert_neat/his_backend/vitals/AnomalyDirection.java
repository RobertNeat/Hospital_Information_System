package robert_neat.his_backend.vitals;

import robert_neat.his_backend.common.wire.WireEnum;

/** `AnomalyDirection` z kontraktu: strona, po ktorej wartosc wykracza poza prog. */
public enum AnomalyDirection implements WireEnum {
    LOW("low"),
    HIGH("high");

    private final String wire;

    AnomalyDirection(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
